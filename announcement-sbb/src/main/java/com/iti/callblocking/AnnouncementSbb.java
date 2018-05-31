package com.iti.callblocking;

import javax.sip.Dialog;
import javax.sip.InvalidArgumentException;

import javax.sip.ServerTransaction;
import javax.sip.SipException;
import javax.sip.address.Address;
import javax.sip.address.AddressFactory;

import javax.sip.header.ContactHeader;
import javax.sip.header.ContentTypeHeader;
import javax.sip.header.HeaderFactory;

import javax.sip.message.MessageFactory;
import javax.sip.message.Request;
import javax.sip.message.Response;
import javax.slee.facilities.Tracer;

import org.mobicents.protocols.mgcp.jain.pkg.AUMgcpEvent;
import org.mobicents.protocols.mgcp.jain.pkg.AUPackage;

import com.iti.callblocking.events.CustomEvent;

import javax.slee.*;

import net.java.slee.resource.mgcp.JainMgcpProvider;
import net.java.slee.resource.mgcp.MgcpActivityContextInterfaceFactory;
import net.java.slee.resource.mgcp.MgcpConnectionActivity;
import net.java.slee.resource.mgcp.MgcpEndpointActivity;
import net.java.slee.resource.sip.DialogActivity;
import net.java.slee.resource.sip.SipActivityContextInterfaceFactory;
import net.java.slee.resource.sip.SleeSipProvider;

import java.text.ParseException;

import javax.naming.Context;
import javax.naming.InitialContext;

import jain.protocol.ip.mgcp.JainMgcpEvent;
import jain.protocol.ip.mgcp.message.CreateConnection;
import jain.protocol.ip.mgcp.message.DeleteConnection;
import jain.protocol.ip.mgcp.message.NotificationRequest;
import jain.protocol.ip.mgcp.message.NotifyResponse;
import jain.protocol.ip.mgcp.message.parms.CallIdentifier;
import jain.protocol.ip.mgcp.message.parms.ConflictingParameterException;
import jain.protocol.ip.mgcp.message.parms.ConnectionDescriptor;
import jain.protocol.ip.mgcp.message.parms.ConnectionIdentifier;
import jain.protocol.ip.mgcp.message.parms.ConnectionMode;
import jain.protocol.ip.mgcp.message.parms.EndpointIdentifier;
import jain.protocol.ip.mgcp.message.parms.EventName;
import jain.protocol.ip.mgcp.message.parms.NotifiedEntity;
import jain.protocol.ip.mgcp.message.parms.RequestedAction;
import jain.protocol.ip.mgcp.message.parms.RequestedEvent;
import jain.protocol.ip.mgcp.message.parms.ReturnCode;
import jain.protocol.ip.mgcp.pkg.MgcpEvent;

public abstract class AnnouncementSbb implements Sbb {

	private Tracer tracer;

	private SipActivityContextInterfaceFactory sipActivityContextInterfaceFactory;
	private SleeSipProvider sipProvider;
	private HeaderFactory headerFactory;
	private AddressFactory addressFactory;
	private MessageFactory messageFactory;
	public final static String ENDPOINT_NAME = "mobicents/ivr/$";
	public final static String JBOSS_BIND_ADDRESS = System.getProperty("jboss.bind.address", "127.0.0.1");
	public final static String WELCOME = "http://" + JBOSS_BIND_ADDRESS + ":8080/mgcpdemo/audio/RQNT-ULAW.wav";
	private JainMgcpProvider mgcpProvider;
	private MgcpActivityContextInterfaceFactory mgcpAcif;
	public static final int MGCP_PEER_PORT = 2427;
	public static final int MGCP_PORT = 2727;
	private SbbContext sbbContext; // This SBB's SbbContext

	public void setSbbContext(SbbContext context) {
		this.sbbContext = context;
		this.tracer = sbbContext.getTracer(AnnouncementSbb.class.getSimpleName());
		try {
			Context ctx = (Context) new InitialContext().lookup("java:comp/env");
			// initialize SIP API
			sipProvider = (SleeSipProvider) ctx.lookup("slee/resources/jainsip/1.2/provider");
			addressFactory = sipProvider.getAddressFactory();
			headerFactory = sipProvider.getHeaderFactory();
			messageFactory = sipProvider.getMessageFactory();
			sipActivityContextInterfaceFactory = (SipActivityContextInterfaceFactory) ctx
					.lookup("slee/resources/jainsip/1.2/acifactory");

			// initialize media api
			mgcpProvider = (JainMgcpProvider) ctx.lookup("slee/resources/jainmgcp/2.0/provider/demo");
			mgcpAcif = (MgcpActivityContextInterfaceFactory) ctx.lookup("slee/resources/jainmgcp/2.0/acifactory/demo");

		} catch (Exception ex) {
			tracer.severe("Could not set SBB context:", ex);
		}

	}

	private void sendRQNT(String mediaPath) {
		EndpointIdentifier endpointID = new EndpointIdentifier(this.getEndpointName(),
				JBOSS_BIND_ADDRESS + ":" + MGCP_PEER_PORT);

		NotificationRequest notificationRequest = new NotificationRequest(this, endpointID,
				mgcpProvider.getUniqueRequestIdentifier());

		EventName[] signalRequests = { new EventName(AUPackage.AU,
				AUMgcpEvent.aupa.withParm("an=" + mediaPath + " it=2")) };

		notificationRequest.setSignalRequests(signalRequests);

		RequestedAction[] actions = new RequestedAction[] { RequestedAction.NotifyImmediately };

		RequestedEvent[] requestedEvents = {
				new RequestedEvent(new EventName(AUPackage.AU, AUMgcpEvent.auoc), actions),
				new RequestedEvent(new EventName(AUPackage.AU, AUMgcpEvent.auof),
						actions) };

		notificationRequest.setRequestedEvents(requestedEvents);
		notificationRequest.setTransactionHandle(mgcpProvider.getUniqueTransactionHandler());
		NotifiedEntity notifiedEntity = new NotifiedEntity(JBOSS_BIND_ADDRESS, JBOSS_BIND_ADDRESS, MGCP_PORT);
		notificationRequest.setNotifiedEntity(notifiedEntity);

		MgcpEndpointActivity endpointActivity = null;
		try {
			endpointActivity = mgcpProvider.getEndpointActivity(endpointID);
			ActivityContextInterface epnAci = mgcpAcif.getActivityContextInterface(endpointActivity);
			epnAci.attach(sbbContext.getSbbLocalObject());
		} catch (FactoryException ex) {
			ex.printStackTrace();
		} catch (NullPointerException ex) {
			ex.printStackTrace();
		} catch (UnrecognizedActivityException ex) {
			ex.printStackTrace();
		}

		mgcpProvider.sendMgcpEvents(new JainMgcpEvent[] { notificationRequest });
	}

	public void unsetSbbContext() {
		this.sbbContext = null;
	}

	protected SbbContext getSbbContext() {
		return sbbContext;
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

	public void onCallCreated(CustomEvent event, ActivityContextInterface aci) {
		
		
		// create Dialog and attach SBB to the Dialog Activity
		ActivityContextInterface daci = null;
		try {
			Dialog dialog = sipProvider.getNewDialog(getServerTransaction());
			dialog.terminateOnBye(true);
			daci = sipActivityContextInterfaceFactory.getActivityContextInterface((DialogActivity) dialog);
			daci.attach(sbbContext.getSbbLocalObject());

		} catch (Exception e) {
			tracer.severe("Error during dialog creation", e);
			return;
		}
		
		// create connection and attach sbb to the connection activity
		CallIdentifier callID = mgcpProvider.getUniqueCallIdentifier();
		this.setCallIdentifier(callID.toString());
		EndpointIdentifier endpointID = new EndpointIdentifier(ENDPOINT_NAME,
				JBOSS_BIND_ADDRESS + ":" + MGCP_PEER_PORT);

		CreateConnection createConnection = new CreateConnection(this, callID, endpointID, ConnectionMode.SendOnly);

		try {
			String sdp = new String(event.getInviteEvent().getRequest().getRawContent());
			createConnection.setRemoteConnectionDescriptor(new ConnectionDescriptor(sdp));
		} catch (ConflictingParameterException e) {
			// should never happen
		}

		int txID = mgcpProvider.getUniqueTransactionHandler();
		createConnection.setTransactionHandle(txID);
		
		
		MgcpConnectionActivity connectionActivity = null;
		try {
			connectionActivity = mgcpProvider.getConnectionActivity(txID, endpointID);
			ActivityContextInterface epnAci = mgcpAcif.getActivityContextInterface(connectionActivity);
			epnAci.attach(sbbContext.getSbbLocalObject());
		} catch (FactoryException ex) {
			ex.printStackTrace();
		} catch (NullPointerException ex) {
			ex.printStackTrace();
		} catch (UnrecognizedActivityException ex) {
			ex.printStackTrace();
		}
		tracer.warning("\n\n\n sending CRCX Request to media server \n\n\n");
		mgcpProvider.sendMgcpEvents(new JainMgcpEvent[] { createConnection });
	}

	public void onBYE(javax.sip.RequestEvent event, ActivityContextInterface aci/* , EventContext eventContext */) {
		
		EndpointIdentifier endpointID = new EndpointIdentifier(this.getEndpointName(),
				JBOSS_BIND_ADDRESS + ":" + MGCP_PEER_PORT);
		DeleteConnection deleteConnection = new DeleteConnection(this, endpointID);

		deleteConnection.setTransactionHandle(mgcpProvider.getUniqueTransactionHandler());
		mgcpProvider.sendMgcpEvents(new JainMgcpEvent[] { deleteConnection });

		ServerTransaction tx = event.getServerTransaction();
		Request request = event.getRequest();

		try {
			Response response = messageFactory.createResponse(Response.OK, request);
			tx.sendResponse(response);
		} catch (Exception e) {
			tracer.severe("Error while sending DLCX ", e);
		}
	}

	public void onCANCEL(net.java.slee.resource.sip.CancelRequestEvent event,
			ActivityContextInterface aci/* , EventContext eventContext */) {
		tracer.warning("\n\n Announcemnt Sbb onCancelMethod\n\n");

		EndpointIdentifier endpointID = new EndpointIdentifier(this.getEndpointName(),
				JBOSS_BIND_ADDRESS + ":" + MGCP_PEER_PORT);
		DeleteConnection deleteConnection = new DeleteConnection(this, endpointID);

		deleteConnection.setTransactionHandle(mgcpProvider.getUniqueTransactionHandler());
		mgcpProvider.sendMgcpEvents(new JainMgcpEvent[] { deleteConnection });

		ServerTransaction tx = event.getServerTransaction();
		Request request = event.getRequest();

		try {
			Response response = messageFactory.createResponse(Response.OK, request);
			tx.sendResponse(response);
		} catch (Exception e) {
			tracer.severe("Error while sending DLCX ", e);
		}
	}

	public void onCREATE_CONNECTION_RESPONSE(jain.protocol.ip.mgcp.message.CreateConnectionResponse event,
			ActivityContextInterface aci/* , EventContext eventContext */) {

		ServerTransaction txn = getServerTransaction();
		Request request = txn.getRequest();

		ReturnCode status = event.getReturnCode();

		switch (status.getValue()) {
		case ReturnCode.TRANSACTION_EXECUTED_NORMALLY:

			this.setEndpointName(event.getSpecificEndpointIdentifier().getLocalEndpointName());

			ConnectionIdentifier connectionIdentifier = event.getConnectionIdentifier();

			this.setConnectionIdentifier(connectionIdentifier.toString());
			String sdp = event.getLocalConnectionDescriptor().toString();

			ContentTypeHeader contentType = null;
			try {
				contentType = headerFactory.createContentTypeHeader("application", "sdp");
			} catch (ParseException ex) {
			}

			String localAddress = sipProvider.getListeningPoints()[0].getIPAddress();
			int localPort = sipProvider.getListeningPoints()[0].getPort();
			Address contactAddress = null;
			try {
				contactAddress = addressFactory.createAddress("sip:" + localAddress + ":" + localPort);
			} catch (ParseException ex) {
			}
			ContactHeader contact = headerFactory.createContactHeader(contactAddress);

			sendRQNT(WELCOME);

			Response response = null;
			try {
				response = messageFactory.createResponse(Response.SESSION_PROGRESS, request, contentType,
						sdp.getBytes());
				response.setHeader(contact);
				txn.sendResponse(response);
			} catch (ParseException ex) {
				tracer.severe("\n\n ParseException while trying to create OK Response\n", ex);
			} catch (InvalidArgumentException ex) {
				tracer.severe(
						"\n\n InvalidArgumentException while trying to send OK message to call Agent, InvalidArgumentException while trying to send OK Response",
						ex);
			} catch (SipException ex) {
				tracer.severe("\n\n SipException: SipException while trying to send OK Response", ex);
			}

			break;
		default:
			try {
				tracer.warning(
						"\n\n inside onCREATE_CONNECTION_RESPONSE: sending SERVER_INTERNAL_ERROR to call agent...\n\n");
				response = messageFactory.createResponse(Response.SERVER_INTERNAL_ERROR, request);
				txn.sendResponse(response);
			} catch (Exception ex) {
				tracer.severe("Exception while trying to send SERVER_INTERNAL_ERROR Response", ex);
			}
		}
	}

	public void onNOTIFICATION_REQUEST_RESPONSE(jain.protocol.ip.mgcp.message.NotificationRequestResponse event,
			ActivityContextInterface aci/* , EventContext eventContext */) {
		ReturnCode status = event.getReturnCode();

		switch (status.getValue()) {
		case ReturnCode.TRANSACTION_EXECUTED_NORMALLY:
			tracer.warning("\n\nThe Announcement should have been started.....\n\n");
			break;
		default:
			ReturnCode rc = event.getReturnCode();
			tracer.warning("\n\n  RQNT failed. Value = " + rc.getValue() + " Comment = " + rc.getComment() + "\n\n");
			EndpointIdentifier endpointID = new EndpointIdentifier(this.getEndpointName(),
					JBOSS_BIND_ADDRESS + ":" + MGCP_PEER_PORT);
			DeleteConnection deleteConnection = new DeleteConnection(this, endpointID);

			deleteConnection.setTransactionHandle(mgcpProvider.getUniqueTransactionHandler());
			mgcpProvider.sendMgcpEvents(new JainMgcpEvent[] { deleteConnection });
			break;
		}
	}

	public void onNOTIFY(jain.protocol.ip.mgcp.message.Notify event,
			ActivityContextInterface aci/* , EventContext eventContext */) {
		tracer.severe("\n\n\n receiving Notify \n\n\n");
		NotifyResponse response = new NotifyResponse(event.getSource(), ReturnCode.Transaction_Executed_Normally);
		response.setTransactionHandle(event.getTransactionHandle());
		tracer.warning(
				"\n\n inside onNotifymethod : sending NotifyResponse with returnCode=Transaction_Executed_Normally........\n\n");
		mgcpProvider.sendMgcpEvents(new JainMgcpEvent[] { response });

		EventName[] observedEvents = event.getObservedEvents();

		for (EventName observedEvent : observedEvents) {

			switch (observedEvent.getEventIdentifier().intValue()) {
			case MgcpEvent.REPORT_ON_COMPLETION:
				tracer.warning("\n\n inside onNotifymethod : Announcemnet Completed NTFY received...\n\n");
				terminateConnections();
				break;
			case MgcpEvent.REPORT_FAILURE:
				tracer.warning("\n\n inside onNotifymethod : Announcemnet Failed received.....\n\n");
				// TODO : Send DLCX and Send BYE to UA
				break;
			default:
				tracer.warning("received Notify :" + event.getSource());
			}
		}
	}

	public void terminateConnections() {

		ServerTransaction txn = getServerTransaction();
		Request request = txn.getRequest();
		String localAddress = sipProvider.getListeningPoints()[0].getIPAddress();
		int localPort = sipProvider.getListeningPoints()[0].getPort();
		Address contactAddress = null;
		try {
			contactAddress = addressFactory.createAddress("sip:" + localAddress + ":" + localPort);
		} catch (ParseException ex) {
		}
		ContactHeader contact = headerFactory.createContactHeader(contactAddress);
		Response response1 = null;
		try {
			response1 = messageFactory.createResponse(Response.TEMPORARILY_UNAVAILABLE, request);
			response1.setHeader(contact);
			txn.sendResponse(response1);
		} catch (ParseException ex) {
			tracer.severe("\n\n ParseException while trying to create OK Response\n", ex);
		} catch (InvalidArgumentException ex) {
			tracer.severe(
					"\n\n InvalidArgumentException while trying to send OK message to call Agent, InvalidArgumentException while trying to send OK Response",
					ex);
		} catch (SipException ex) {
			tracer.severe("\n\n SipException: SipException while trying to send OK Response", ex);
		}

		EndpointIdentifier endpointID = new EndpointIdentifier(this.getEndpointName(),
				JBOSS_BIND_ADDRESS + ":" + MGCP_PEER_PORT);
		DeleteConnection deleteConnection = new DeleteConnection(this, endpointID);

		deleteConnection.setTransactionHandle(mgcpProvider.getUniqueTransactionHandler());
		mgcpProvider.sendMgcpEvents(new JainMgcpEvent[] { deleteConnection });

	}

	// TODO: Implement the lifecycle methods if required
	public void sbbCreate() throws javax.slee.CreateException {
	}

	public void sbbPostCreate() throws javax.slee.CreateException {
	}

	public void sbbActivate() {
	}

	public void sbbPassivate() {
	}

	public void sbbRemove() {
	}

	public void sbbLoad() {
	}

	public void sbbStore() {
	}

	public void sbbExceptionThrown(Exception exception, Object event, ActivityContextInterface activity) {
	}

	public void sbbRolledBack(RolledBackContext context) {
	}

	// 'connectionIdentifier' CMP field setter
	public abstract void setConnectionIdentifier(String value);

	// 'ConnectionIdentifier' CMP field getter
	public abstract String getConnectionIdentifier();

	// 'callIdentifier' CMP field setter
	public abstract void setCallIdentifier(String value);

	// 'callIdentifier' CMP field getter
	public abstract String getCallIdentifier();

	// 'remoteSdp' CMP field setter
	public abstract void setRemoteSdp(String value);

	// 'remoteSdp' CMP field getter
	public abstract String getRemoteSdp();

	// 'endpointName' CMP field setter
	public abstract void setEndpointName(String value);

	// 'endpointName' CMP field getter
	public abstract String getEndpointName();

}
