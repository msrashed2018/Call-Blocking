package com.iti.callblocking;

import javax.sip.ClientTransaction;
import javax.sip.Dialog;
import javax.sip.DialogState;
import javax.sip.InvalidArgumentException;
import javax.sip.RequestEvent;
import javax.sip.ResponseEvent;
import javax.sip.ServerTransaction;
import javax.sip.SipException;

import javax.sip.address.AddressFactory;
import javax.sip.address.URI;
import javax.sip.header.CSeqHeader;

import javax.sip.header.FromHeader;

import javax.sip.message.Request;
import javax.sip.message.Response;
import javax.slee.facilities.Tracer;

import javax.slee.*;
import net.java.slee.resource.sip.DialogActivity;
import net.java.slee.resource.sip.SipActivityContextInterfaceFactory;
import net.java.slee.resource.sip.SleeSipProvider;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;

import org.mobicents.slee.SbbContextExt;
import com.iti.callblocking.events.CustomEvent;

public abstract class B2buaSbb implements Sbb {

	private Tracer tracer;

	private SipActivityContextInterfaceFactory sipActivityContextInterfaceFactory;
	private SleeSipProvider sipProvider;

	private SbbContext sbbContext; // This SBB's SbbContext
	// TODO: Perform further operations if required in these methods.

	public void setSbbContext(SbbContext context) {
		this.sbbContext = context;

		if (tracer == null) {
			tracer = sbbContext.getTracer(B2buaSbb.class.getSimpleName());
		}
		// initialize SIP API
		try {
			final Context ctx = (Context) new InitialContext().lookup("java:comp/env");
			sipActivityContextInterfaceFactory = (SipActivityContextInterfaceFactory) ctx
					.lookup("slee/resources/jainsip/1.2/acifactory");
			sipProvider = (SleeSipProvider) ctx.lookup("slee/resources/jainsip/1.2/provider");

		} catch (NamingException e) {
			tracer.severe(e.getMessage(), e);
		}

	}

	public void unsetSbbContext() {
		this.sbbContext = null;
	}

	protected SbbContext getSbbContext() {
		return sbbContext;
	}

	private void replyToRequestEvent(RequestEvent event, int status) {
		try {
			event.getServerTransaction()
					.sendResponse(sipProvider.getMessageFactory().createResponse(status, event.getRequest()));
		} catch (Throwable e) {
			tracer.severe("Failed to reply to request event:\n" + event, e);
		}
	}

	private void processMidDialogRequest(RequestEvent event, ActivityContextInterface dialogACI) {
		try {
			// Find the dialog to forward the request on
			ActivityContextInterface peerACI = getPeerDialog(dialogACI);
			DialogActivity da = (DialogActivity) peerACI.getActivity();
			URI to = da.getRemoteTarget().getURI();
			forwardRequest(event, (DialogActivity) peerACI.getActivity(), to);
		} catch (SipException e) {
			tracer.severe(e.getMessage(), e);
			replyToRequestEvent(event, Response.SERVICE_UNAVAILABLE);
		}
	}

	private void processResponse(ResponseEvent event, ActivityContextInterface aci) {
		try {
			// Find the dialog to forward the response on
			ActivityContextInterface peerACI = getPeerDialog(aci);
			forwardResponse((DialogActivity) aci.getActivity(), (DialogActivity) peerACI.getActivity(),
					event.getClientTransaction(), event.getResponse());
		} catch (SipException e) {
			tracer.severe(e.getMessage(), e);
		}
	}

	private ActivityContextInterface getPeerDialog(ActivityContextInterface aci) throws SipException {
		final ActivityContextInterface incomingDialogAci = getIncomingDialog();
		if (aci.equals(incomingDialogAci)) {
			return getOutgoingDialog();
		}
		if (aci.equals(getOutgoingDialog())) {
			return incomingDialogAci;
		}
		throw new SipException("could not find peer dialog");

	}

	private void forwardRequest(RequestEvent event, DialogActivity out, URI to) throws SipException {

		final Request incomingRequest = event.getRequest();
		tracer.warning("\n\nForwarding request " + incomingRequest.getMethod() + " to dialog " + out);
		// Copies the request, setting the appropriate headers for the dialog.
		Request outgoingRequest = out.createRequest(incomingRequest);
		outgoingRequest.setRequestURI(to);

		tracer.warning("\n\n\nrequest Uri=" + outgoingRequest.getRequestURI() + "...........\n\n\n");
		// Send the request on the dialog activity
		final ClientTransaction ct = out.sendRequest(outgoingRequest);
		// Record an association with the original server transaction,
		// so we can retrieve it when forwarding the response.
		out.associateServerTransaction(ct, event.getServerTransaction());
	}

	private void forwardResponse(DialogActivity in, DialogActivity out, ClientTransaction ct, Response receivedResponse)
			throws SipException {
		// Find the original server transaction that this response
		// should be forwarded on.
		final ServerTransaction st = in.getAssociatedServerTransaction(ct);
		// could be null
		if (st == null)
			throw new SipException("\n\n could not find associated server transaction");
		if (tracer.isInfoEnabled()) {
			tracer.warning("Forwarding response " + receivedResponse.getStatusCode() + " to dialog " + out);
		}
		// Copy the response across, setting the appropriate headers for the
		// dialog
		final Response outgoingResponse = out.createResponse(st, receivedResponse);
		// Forward response upstream.
		try {
			st.sendResponse(outgoingResponse);
		} catch (InvalidArgumentException e) {
			tracer.severe("Failed to send response:\n" + outgoingResponse, e);
			throw new SipException("invalid response", e);
		}
	}

	private ServerTransaction getServerTransaction() {
		ActivityContextInterface[] activities = sbbContext.getActivities();
		for (ActivityContextInterface activity : activities) {
			if (activity.getActivity() instanceof ServerTransaction) {
				return (ServerTransaction) activity.getActivity();
			}
		}
		return null;
	}

	public void onCallCreated(CustomEvent event, ActivityContextInterface aci/* , EventContext eventContext */) {

		try {
			final DialogActivity incomingDialog = (DialogActivity) sipProvider.getNewDialog(getServerTransaction());
			final ActivityContextInterface incomingDialogACI = sipActivityContextInterfaceFactory
					.getActivityContextInterface(incomingDialog);

			final DialogActivity outgoingDialog = sipProvider.getNewDialog(incomingDialog, true);
			final ActivityContextInterface outgoingDialogACI = sipActivityContextInterfaceFactory
					.getActivityContextInterface(outgoingDialog);

			final SbbLocalObject sbbLocalObject = sbbContext.getSbbLocalObject();

			incomingDialogACI.attach(sbbLocalObject);
			outgoingDialogACI.attach(sbbLocalObject);

			setIncomingDialog(incomingDialogACI);
			setOutgoingDialog(outgoingDialogACI);
			forwardRequest(event.getInviteEvent(), outgoingDialog, event.getbPartyUri());
		} catch (SipException e) {
			e.printStackTrace();
		}

	}



	public void onBYE(javax.sip.RequestEvent event, ActivityContextInterface aci/* , EventContext eventContext */) {
		tracer.warning("\n\n\n B2bua Sbb inside onBYE Method \n\n\n");
		replyToRequestEvent(event, Response.OK);
		processMidDialogRequest(event, aci);

	}

	public void onCANCEL(net.java.slee.resource.sip.CancelRequestEvent event,
			ActivityContextInterface aci/* , EventContext eventContext */) {
		tracer.warning("\n\n\n hereeee.......\n\n\n");
		tracer.warning("\n\n\n B2bua Sbb  inside onCancel Method \n\n\n");
		if (tracer.isInfoEnabled()) {
			tracer.info("Got a CANCEL request.");
		}
		try {
			this.sipProvider.acceptCancel(event, false);
			final ActivityContextInterface peerDialogACI = getOutgoingDialog();
			final DialogActivity peerDialog = (DialogActivity) peerDialogACI.getActivity();
			final DialogState peerDialogState = peerDialog.getState();
			if (peerDialogState == null || peerDialogState == DialogState.EARLY) {
				peerDialog.sendCancel();
			} else {
				peerDialog.sendRequest(peerDialog.createRequest(Request.BYE));
			}
		} catch (Exception e) {
			tracer.severe("Failed to process cancel request", e);
		}
	}

	public void on2xxResponse(javax.sip.ResponseEvent event,
			ActivityContextInterface aci/* , EventContext eventContext */) {

		final CSeqHeader cseq = (CSeqHeader) event.getResponse().getHeader(CSeqHeader.NAME);
		if (cseq.getMethod().equals(Request.INVITE)) {

			try {
				final Request ack = event.getDialog().createAck(cseq.getSeqNumber());
				event.getDialog().sendAck(ack);
			} catch (Exception e) {
				tracer.severe("Unable to ack INVITE's 200 ok from UAS", e);
			}
		} else if (cseq.getMethod().equals(Request.BYE) || cseq.getMethod().equals(Request.CANCEL)) {
			// not forwarded to the other dialog
			return;
		}
		processResponse(event, aci);
	}

	public void on1xxResponse(javax.sip.ResponseEvent event,
			ActivityContextInterface aci/* , EventContext eventContext */) {

		if (event.getResponse().getStatusCode() == Response.TRYING) {
			// those are not forwarded to the other dialog
			return;
		}
		processResponse(event, aci);
	}

	public void on4xxResponse(javax.sip.ResponseEvent event,
			ActivityContextInterface aci/* , EventContext eventContext */) {

		processResponse(event, aci);
	}

	public void on6xxResponse(javax.sip.ResponseEvent event,
			ActivityContextInterface aci/* , EventContext eventContext */) {

		if (event.getResponse().getStatusCode() == Response.DECLINE) {
			processResponse(event, aci);
			return;
		}
	}

	// TODO: Implement the lifecycle methods if required
	public void sbbCreate() throws javax.slee.CreateException {
		// tracer.warning("\n\n\n Call Sbb inside sbbCreate Method");

	}

	public void sbbPostCreate() throws javax.slee.CreateException {
		// tracer.warning("\n\n\n Call Sbb inside sbbPostCreate Method\n\n");
	}

	public void sbbActivate() {
		// tracer.warning("\n\n\n Call Sbb inside sbbActivate Method\\n\\n");
	}

	public void sbbPassivate() {
		// tracer.warning("\n\n\n Call Sbb inside sbbPassivate Method\\n\\n");
	}

	public void sbbRemove() {
		// tracer.warning("\n\n\n Call Sbb inside sbbRemove Method\\n\\n");
	}

	public void sbbLoad() {
		// tracer.warning("\n\n\n Call Sbb inside sbbLoad Method\\n\\n");
	}

	public void sbbStore() {
		// tracer.warning("\n\n\n Call Sbb inside sbbStrore Method\\n\\n");
	}

	public void sbbExceptionThrown(Exception exception, Object event, ActivityContextInterface activity) {
	}

	public void sbbRolledBack(RolledBackContext context) {}

	// 'incomingDialog' CMP field setter
	public abstract void setIncomingDialog(javax.slee.ActivityContextInterface value);

	// 'incomingDialog' CMP field getter
	public abstract javax.slee.ActivityContextInterface getIncomingDialog();

	// 'outgoingDialog' CMP field setter
	public abstract void setOutgoingDialog(javax.slee.ActivityContextInterface value);

	// 'outgoingDialog' CMP field getter
	public abstract javax.slee.ActivityContextInterface getOutgoingDialog();

}
