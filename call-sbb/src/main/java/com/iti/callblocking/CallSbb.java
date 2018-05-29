package com.iti.callblocking;


import javax.sip.ClientTransaction;
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

import org.restcomm.slee.resource.jdbc.JdbcActivityContextInterfaceFactory;
import org.restcomm.slee.resource.jdbc.JdbcResourceAdaptorSbbInterface;

import javax.slee.*;


import net.java.slee.resource.sip.DialogActivity;
import net.java.slee.resource.sip.SipActivityContextInterfaceFactory;
import net.java.slee.resource.sip.SleeSipProvider;


import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;


import org.mobicents.slee.SbbContextExt;
import org.restcomm.slee.resource.jdbc.JdbcActivity;


import com.iti.database.CallBlockingDBMS;


public abstract class CallSbb implements Sbb {
	private SbbContextExt contextExt;
	private JdbcActivityContextInterfaceFactory jdbcACIF;
	private JdbcResourceAdaptorSbbInterface jdbcRA;

	private  Tracer tracer;


	private SipActivityContextInterfaceFactory sipActivityContextInterfaceFactory;
	private SleeSipProvider sipProvider;

	private AddressFactory addressFactory;

	public final static String ENDPOINT_NAME = "mobicents/ivr/$";
	public final static String JBOSS_BIND_ADDRESS = System.getProperty("jboss.bind.address", "127.0.0.1");
	public final static String WELCOME = "http://" + JBOSS_BIND_ADDRESS + ":8080/mgcpdemo/audio/RQNT-ULAW.wav";

	public static final int MGCP_PEER_PORT = 2427;
	public static final int MGCP_PORT = 2727;
	private SbbContext sbbContext; // This SBB's SbbContext
	// TODO: Perform further operations if required in these methods.
	public void setSbbContext(SbbContext context) { 
		this.sbbContext = context;
		this.contextExt = (SbbContextExt) context;
			
			this.jdbcRA = (JdbcResourceAdaptorSbbInterface) contextExt.getResourceAdaptorInterface(
					JdbcResourceAdaptorSbbInterface.RATYPE_ID, "JDBCRA");
	        this.jdbcACIF = (JdbcActivityContextInterfaceFactory) contextExt.getActivityContextInterfaceFactory(JdbcActivityContextInterfaceFactory.RATYPE_ID);

		if (tracer == null) {
			tracer = sbbContext.getTracer(CallSbb.class
					.getSimpleName());
		}
		
		try {
			final Context ctx = (Context) new InitialContext()
					.lookup("java:comp/env");
			sipActivityContextInterfaceFactory = (SipActivityContextInterfaceFactory) ctx
					.lookup("slee/resources/jainsip/1.2/acifactory");
			sipProvider = (SleeSipProvider) ctx
					.lookup("slee/resources/jainsip/1.2/provider");
			addressFactory=sipProvider.getAddressFactory();

		} catch (NamingException e) {
			tracer.severe("\n\n\n error while instializing mgcp and sip API inside setSbbContext method\n\n");
			tracer.severe(e.getMessage(), e);
		}
		
	}
	
	
	public void unsetSbbContext() { this.sbbContext = null; }
	
	protected SbbContext getSbbContext() {
		return sbbContext;
	}

	
	private void replyToRequestEvent(RequestEvent event, int status) {
		try {
			event.getServerTransaction().sendResponse(
					sipProvider.getMessageFactory().createResponse(status,
							event.getRequest()));
		} catch (Throwable e) {
			tracer.severe("Failed to reply to request event:\n" + event, e);
		}		
	}


	private void processMidDialogRequest(RequestEvent event, ActivityContextInterface dialogACI) {
		try {
			// Find the dialog to forward the request on
			ActivityContextInterface peerACI = getPeerDialog(dialogACI);
			DialogActivity da=(DialogActivity) peerACI.getActivity();
			URI to=da.getRemoteTarget().getURI();
			forwardRequest(event,(DialogActivity) peerACI.getActivity(),to);
		} catch (SipException e) {
			tracer.severe(e.getMessage(), e);
			replyToRequestEvent(event, Response.SERVICE_UNAVAILABLE);
		}
	}

	private void processResponse(ResponseEvent event,
			ActivityContextInterface aci) {
		try {
			// Find the dialog to forward the response on
			ActivityContextInterface peerACI = getPeerDialog(aci);
			forwardResponse((DialogActivity) aci.getActivity(),
					(DialogActivity) peerACI.getActivity(), event
							.getClientTransaction(), event.getResponse());
		} catch (SipException e) {
			tracer.severe(e.getMessage(), e);
		}
	}

	private ActivityContextInterface getPeerDialog(ActivityContextInterface aci)
			throws SipException {
		final ActivityContextInterface incomingDialogAci = getIncomingDialog();
		if (aci.equals(incomingDialogAci)) {
			return getOutgoingDialog();
		}
		if (aci.equals(getOutgoingDialog())) {
			return incomingDialogAci;
		}
		throw new SipException("could not find peer dialog");

	}

	private void forwardRequest(RequestEvent event, DialogActivity out,URI to)
			throws SipException {

		final Request incomingRequest = event.getRequest();
		if (tracer.isInfoEnabled()) {

			tracer.info("\n\nForwarding request " + incomingRequest.getMethod()
					+ " to dialog " + out);
		}
		// Copies the request, setting the appropriate headers for the dialog.
		Request outgoingRequest = out.createRequest(incomingRequest);
		outgoingRequest.setRequestURI(to);

		tracer.warning("\n\n\nrequest Uri="+outgoingRequest.getRequestURI()+"...........\n\n\n");
//		 Send the request on the dialog activity
		final ClientTransaction ct = out.sendRequest(outgoingRequest);
		// Record an association with the original server transaction,
		// so we can retrieve it when forwarding the response.
		out.associateServerTransaction(ct, event.getServerTransaction());
	}

	private void forwardResponse(DialogActivity in, DialogActivity out,
			ClientTransaction ct, Response receivedResponse)
			throws SipException {
		// Find the original server transaction that this response
		// should be forwarded on.
		final ServerTransaction st = in.getAssociatedServerTransaction(ct);
		// could be null
		if (st == null)
			throw new SipException(
					"could not find associated server transaction");
		if (tracer.isInfoEnabled()) {
			tracer.info("Forwarding response "
					+ receivedResponse.getStatusCode() + " to dialog " + out);
		}
		// Copy the response across, setting the appropriate headers for the
		// dialog
		final Response outgoingResponse = out.createResponse(st,
				receivedResponse);
		// Forward response upstream.
		try {
			st.sendResponse(outgoingResponse);
		} catch (InvalidArgumentException e) {
			tracer.severe("Failed to send response:\n" + outgoingResponse, e);
			throw new SipException("invalid response", e);
		}
	}

	public void forwardToAnnouncementSbb(ActivityContextInterface aci) {
		tracer.warning("\n\n create announcement child SBB");
        ChildRelation relation = getAnnouncementSbbChild();
        SbbLocalObject child;
		try {
			child = relation.create();
			aci.attach(child);
			tracer.warning("\n\n attaching Announcement childSbb to activity context interface ,,");
			aci.detach(sbbContext.getSbbLocalObject());
			tracer.warning("\n\n deattaching Call parent Sbb from activity context interface ,,");
			tracer.warning("\n\n call Parent Sbb=,,"+sbbContext.getSbbLocalObject());
			
		} catch (TransactionRequiredLocalException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (SLEEException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (CreateException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		
	}
	public void onINVITE(javax.sip.RequestEvent event, ActivityContextInterface aci/*, EventContext eventContext*/) {
		// ACI is the server transaction activity
				try {
					tracer.severe("\n\n\n Call Sbb inside onINVITE Method \n\n\n");
					// send "trying" response
					replyToRequestEvent(event, Response.TRYING);
					Request request = event.getRequest();
					FromHeader fromHeader = (FromHeader) request.getHeader(FromHeader.NAME);
		            String[] partyA = fromHeader.getAddress().getURI().toString().split("@|\\:");
		            String[] partyB = request.getRequestURI().toString().split("@|\\:");
		            int Bparty = Integer.valueOf(partyB[1]);
		            CallBlockingDBMS mng = new CallBlockingDBMS(); 
					// create activity using the RA sbb interface
			        JdbcActivity jdbcActivity =jdbcRA.createActivity();
			        // get its aci from the RA ACI factory
			        ActivityContextInterface jdbcACI = jdbcACIF.getActivityContextInterface(jdbcActivity);
			        // attach the sbb entity
			        jdbcACI.attach(contextExt.getSbbLocalObject());
			        jdbcActivity.execute(mng.getUserAddress(Bparty));
			        jdbcActivity.execute(mng.HasWhiteList(Bparty));
		            jdbcActivity.execute(mng.IsWhiteTimeFrame(Bparty));
		            jdbcActivity.execute(mng.InWhiteList(Bparty, partyA[1]));
		            jdbcActivity.execute(mng.HasBlackList(Bparty));
		            jdbcActivity.execute(mng.IsBlackTimeFrame(Bparty));
		            jdbcActivity.execute(mng.InBlackList(Bparty, partyA[1]));
		            tracer.severe("\n\n\nsleeping 7...............\n\n\n");
		            																														Thread.sleep(10);
		            final DialogActivity incomingDialog = (DialogActivity) sipProvider
							.getNewDialog(event.getServerTransaction());
					final DialogActivity outgoingDialog = sipProvider.getNewDialog(
							incomingDialog, true);
					final ActivityContextInterface outgoingDialogACI = sipActivityContextInterfaceFactory
							.getActivityContextInterface(outgoingDialog);		
					final ActivityContextInterface incomingDialogACI = sipActivityContextInterfaceFactory
							.getActivityContextInterface(incomingDialog);
					final SbbLocalObject sbbLocalObject = sbbContext
							.getSbbLocalObject();

					incomingDialogACI.attach(sbbLocalObject);
					outgoingDialogACI.attach(sbbLocalObject);

					setIncomingDialog(incomingDialogACI);
					setOutgoingDialog(outgoingDialogACI);
		            
		            
		            if (mng.HasWhiteList && mng.IsWhiteTimeFrame && !mng.InWhiteList) {
		                	forwardToAnnouncementSbb(aci);
		            } else if (mng.HasBlackList && mng.IsBlackTimeFrame && mng.InBlackList) {
		                	forwardToAnnouncementSbb(aci);
		            }
		            else {
		            	URI to=addressFactory.createURI("sip:"+Bparty+"@"+mng.address);
						forwardRequest(event, outgoingDialog,to);
		            }

				} catch (Throwable e) {
					tracer.severe("\n\n\n Failed to process incoming INVITE.", e);
					replyToRequestEvent(event, Response.SERVICE_UNAVAILABLE);
				}	
	}
	
	
	public void onBYE(javax.sip.RequestEvent event, ActivityContextInterface aci/*, EventContext eventContext*/) {
		tracer.warning("\n\n\n Call Sbb inside onBYE Method \n\n\n");
		replyToRequestEvent(event, Response.OK);
		processMidDialogRequest(event, aci);	


	}
	public void onCANCEL(net.java.slee.resource.sip.CancelRequestEvent event, ActivityContextInterface aci/*, EventContext eventContext*/) {
		tracer.warning("\n\n\n Call Sbb  inside onCancel Method \n\n\n");
		if (tracer.isInfoEnabled()) {
			tracer.info("Got a CANCEL request.");
		}
		try {
			this.sipProvider.acceptCancel(event, false);
			final ActivityContextInterface peerDialogACI = getOutgoingDialog();
			final DialogActivity peerDialog = (DialogActivity) peerDialogACI
					.getActivity();
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

	public void on2xxResponse(javax.sip.ResponseEvent event, ActivityContextInterface aci/*, EventContext eventContext*/) {
		final CSeqHeader cseq = (CSeqHeader) event.getResponse().getHeader(
				CSeqHeader.NAME);
		if (cseq.getMethod().equals(Request.INVITE)) {

			try {
				final Request ack = event.getDialog().createAck(
						cseq.getSeqNumber());
				event.getDialog().sendAck(ack);
			} catch (Exception e) {
				tracer.severe("Unable to ack INVITE's 200 ok from UAS", e);
			}
		} else if (cseq.getMethod().equals(Request.BYE)
				|| cseq.getMethod().equals(Request.CANCEL)) {
			// not forwarded to the other dialog
			return;
		}
		processResponse(event, aci);
	}

	public void on1xxResponse(javax.sip.ResponseEvent event, ActivityContextInterface aci/*, EventContext eventContext*/) {
		if (event.getResponse().getStatusCode() == Response.TRYING) {
			// those are not forwarded to the other dialog
			return;
		}
		processResponse(event, aci);
	}
	
	public void on4xxResponse(javax.sip.ResponseEvent event, ActivityContextInterface aci/*, EventContext eventContext*/) {
		tracer.severe("\n\n\nreceiving 4xxResponse \n\n\n\n");
		processResponse(event, aci);
	}


	public void onSimpleJdbcTaskResultEvent(org.restcomm.slee.resource.jdbc.task.simple.SimpleJdbcTaskResultEvent event, ActivityContextInterface aci/*, EventContext eventContext*/) {
//		  tracer.warning("\n\n\nReceived a SimpleJdbcTaskResultEvent  \n\nresult object = " + event.getResult().toString()+"\n\n");
//		  tracer.info("\n\n\n");
//        ((JdbcActivity) aci.getActivity()).endActivity();
//        tracer.info("\n\n\n");
	}


	public void onJdbcTaskExecutionThrowableEvent(org.restcomm.slee.resource.jdbc.event.JdbcTaskExecutionThrowableEvent event, ActivityContextInterface aci/*, EventContext eventContext*/) {
		tracer.severe("\n\n\n\n error from db??????? \n\n "+event.toString());
		tracer.info("\n\n\n");
		event.getThrowable().printStackTrace();
		tracer.info("\n\n\n");
	}


	public void on6xxResponse(javax.sip.ResponseEvent event, ActivityContextInterface aci/*, EventContext eventContext*/) {
		tracer.severe("\n\n\nreceiving 6xxResponse \n\n\n\n");
		if (event.getResponse().getStatusCode() == Response.DECLINE) {
			processResponse(event, aci);
			return;
		}
	}

	// TODO: Implement the lifecycle methods if required
	public void sbbCreate() throws javax.slee.CreateException {
		tracer.severe("\n\n\n Call Sbb inside sbbCreate Method");
		
	}
	public void sbbPostCreate() throws javax.slee.CreateException {
		tracer.severe("\n\n\n Call Sbb inside sbbPostCreate Method\n\n");
	}
	public void sbbActivate() {
		tracer.severe("\n\n\n Call Sbb inside sbbActivate Method\\n\\n");
	}
	public void sbbPassivate() {
		tracer.severe("\n\n\n Call Sbb inside sbbPassivate Method\\n\\n");
	}
	public void sbbRemove() {
		tracer.severe("\n\n\n Call Sbb inside sbbRemove Method\\n\\n");
	}
	public void sbbLoad() {
		tracer.severe("\n\n\n Call Sbb inside sbbLoad Method\\n\\n");
	}
	public void sbbStore() {
		tracer.severe("\n\n\n Call Sbb inside sbbStrore Method\\n\\n");
	}
	public void sbbExceptionThrown(Exception exception, Object event, ActivityContextInterface activity) {
		
		tracer.severe("\n\n\n Call Sbb inside sbbExceptionThrown Method");	}
	public void sbbRolledBack(RolledBackContext context) {
		tracer.severe("\n\n\n Call Sbb inside sbbRolledBack Method");
	}

	public abstract ChildRelation getAnnouncementSbbChild();


	// 'incomingDialog' CMP field setter
	public abstract void setIncomingDialog(javax.slee.ActivityContextInterface value);


	// 'incomingDialog' CMP field getter
	public abstract javax.slee.ActivityContextInterface getIncomingDialog();


	// 'outgoingDialog' CMP field setter
	public abstract void setOutgoingDialog(javax.slee.ActivityContextInterface value);


	// 'outgoingDialog' CMP field getter
	public abstract javax.slee.ActivityContextInterface getOutgoingDialog();

}
