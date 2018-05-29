package com.iti.callblocking;


import javax.sip.Dialog;

import javax.sip.InvalidArgumentException;
import javax.sip.RequestEvent;

import javax.sip.ServerTransaction;
import javax.sip.SipException;
import javax.sip.address.Address;
import javax.sip.address.AddressFactory;

import javax.sip.header.ContactHeader;
import javax.sip.header.ContentTypeHeader;
import javax.sip.header.FromHeader;
import javax.sip.header.HeaderFactory;
import javax.sip.header.ToHeader;
import javax.sip.message.MessageFactory;
import javax.sip.message.Request;
import javax.sip.message.Response;
import javax.slee.facilities.Tracer;

import org.restcomm.slee.resource.jdbc.JdbcActivityContextInterfaceFactory;
import org.restcomm.slee.resource.jdbc.JdbcResourceAdaptorSbbInterface;

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
import javax.naming.NamingException;

import org.mobicents.slee.SbbContextExt;



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
import jain.protocol.ip.mgcp.pkg.PackageName;

public abstract class AnnouncementSbb implements Sbb {
//	private SbbContextExt contextExt;
//	private JdbcActivityContextInterfaceFactory jdbcACIF;
//	private JdbcResourceAdaptorSbbInterface jdbcRA;
//	private String address;
	private  Tracer tracer;
//	private boolean msConnctionCreated=false;

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
	// TODO: Perform further operations if required in these methods.
	public void setSbbContext(SbbContext context) { 
		
		this.sbbContext = context;
		this.tracer = sbbContext.getTracer(AnnouncementSbb.class.getSimpleName());
		
		tracer.severe("\n\n\n Announcement SBB , inside setSbbContext\n\n\n");
		try {
			Context ctx = (Context) new InitialContext().lookup("java:comp/env");

			// initialize SIP API
			sipProvider = (SleeSipProvider) ctx.lookup("slee/resources/jainsip/1.2/provider");

			addressFactory = sipProvider.getAddressFactory();
			headerFactory = sipProvider.getHeaderFactory();
			messageFactory = sipProvider.getMessageFactory();
			sipActivityContextInterfaceFactory = (SipActivityContextInterfaceFactory) ctx.lookup("slee/resources/jainsip/1.2/acifactory");

			// initialize media api

			mgcpProvider = (JainMgcpProvider) ctx.lookup("slee/resources/jainmgcp/2.0/provider/demo");
			mgcpAcif = (MgcpActivityContextInterfaceFactory) ctx.lookup("slee/resources/jainmgcp/2.0/acifactory/demo");

		} catch (Exception ne) {
			tracer.severe("Could not set SBB context:", ne);
		}

	}
	
	private void sendRQNT(String mediaPath, boolean createActivity) {
		tracer.severe("\n\n\n Announcement SBB , inside sendRQNT\n\n\n");
		EndpointIdentifier endpointID = new EndpointIdentifier(this.getEndpointName(), JBOSS_BIND_ADDRESS + ":"
				+ MGCP_PEER_PORT);
		tracer.warning("\n\n inside sendRQNT method:EndpointIdentifier is created :\n endpoint domainName="+endpointID.getDomainName()+"\nendpoint local endpointName:"+endpointID.getLocalEndpointName()+" \n\n ");

		NotificationRequest notificationRequest = new NotificationRequest(this, endpointID, mgcpProvider
				.getUniqueRequestIdentifier());
		
//		ConnectionIdentifier connectionIdentifier = new ConnectionIdentifier(this.getConnectionIdentifier());
		
		EventName[] signalRequests = { new EventName(PackageName.Announcement, MgcpEvent.ann.withParm(mediaPath)/*,connectionIdentifier*/) };
		notificationRequest.setSignalRequests(signalRequests);
		
		RequestedAction[] actions = new RequestedAction[] { RequestedAction.NotifyImmediately };
//
//		RequestedEvent[] requestedEvents = {
//				new RequestedEvent(new EventName(PackageName.Dtmf, MgcpEvent.dtmf0/* , connectionIdentifier */), actions),
//				new RequestedEvent(new EventName(PackageName.Dtmf, MgcpEvent.dtmf1/* , connectionIdentifier */), actions)};

//		notificationRequest.setRequestedEvents(requestedEvents);
		
		
		notificationRequest.setTransactionHandle(mgcpProvider.getUniqueTransactionHandler());

		NotifiedEntity notifiedEntity = new NotifiedEntity(JBOSS_BIND_ADDRESS, JBOSS_BIND_ADDRESS, MGCP_PORT);
		notificationRequest.setNotifiedEntity(notifiedEntity);

		if (createActivity) {
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
		} // if (createActivity)

		mgcpProvider.sendMgcpEvents(new JainMgcpEvent[] { notificationRequest });

		tracer.info("============================= \nNotificationRequest sent:\n"+notificationRequest+"\n=============================");
	}
	public void unsetSbbContext() { this.sbbContext = null; }
	
	protected SbbContext getSbbContext() {
		return sbbContext;
	}

	
	private void replyToRequestEvent(RequestEvent event, int status) {
		tracer.severe("\n\n\n Announcement SBB , inside replyToRequestEvent\n\n\n");
		try {
			event.getServerTransaction().sendResponse(
					sipProvider.getMessageFactory().createResponse(status,
							event.getRequest()));
		} catch (Throwable e) {
			tracer.severe("Failed to reply to request event:\n" + event, e);
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



	public void onINVITE(javax.sip.RequestEvent event, ActivityContextInterface aci/*, EventContext eventContext*/) {
		tracer.severe("\n\n\n Announcement SBB , inside onInviteMethod\n\n\n");
		Request request = event.getRequest();

		FromHeader from = (FromHeader) request.getHeader(FromHeader.NAME);
		ToHeader to = (ToHeader) request.getHeader(ToHeader.NAME);

		tracer.info("Incoming call " + from + " " + to);
		tracer.severe("\n\n \n AnnouncementSBB inside onInvite\n\n\n");
		// create Dialog and attach SBB to the Dialog Activity
		ActivityContextInterface daci = null;
		try {
			Dialog dialog = sipProvider.getNewDialog(event.getServerTransaction());
			dialog.terminateOnBye(true);
			daci = sipActivityContextInterfaceFactory.getActivityContextInterface((DialogActivity) dialog);
			daci.attach(sbbContext.getSbbLocalObject());
		} catch (Exception e) {
			tracer.severe("Error during dialog creation", e);
			replyToRequestEvent(event, Response.SERVER_INTERNAL_ERROR);
			return;
		}

		// respond(evt, Response.RINGING);

		CallIdentifier callID = mgcpProvider.getUniqueCallIdentifier();
		this.setCallIdentifier(callID.toString());
		EndpointIdentifier endpointID = new EndpointIdentifier(ENDPOINT_NAME, JBOSS_BIND_ADDRESS + ":" + MGCP_PEER_PORT);

		CreateConnection createConnection = new CreateConnection(this, callID, endpointID, ConnectionMode.SendRecv);

		try {
			String sdp = new String(event.getRequest().getRawContent());
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

		mgcpProvider.sendMgcpEvents(new JainMgcpEvent[] { createConnection });
	}
	
	
	public void onBYE(javax.sip.RequestEvent event, ActivityContextInterface aci/*, EventContext eventContext*/) {
		tracer.severe("\n\n\n Announcement SBB , inside onBye\n\n\n");
		EndpointIdentifier endpointID = new EndpointIdentifier(this.getEndpointName(), JBOSS_BIND_ADDRESS + ":"
				+ MGCP_PEER_PORT);
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
	// TODO: Implement the lifecycle methods if required
	public void sbbCreate() throws javax.slee.CreateException {
		tracer.severe("\n\n\n Announcement Sbb inside sbbCreate Method");
		
	}
	public void sbbPostCreate() throws javax.slee.CreateException {
		tracer.severe("\n\n\n Announcement Sbb inside sbbPostCreate Method\n\n");
	}
	public void sbbActivate() {
		tracer.severe("\n\n\n Announcement Sbb inside sbbActivate Method\\n\\n");
	}
	public void sbbPassivate() {
		tracer.severe("\n\n\n Announcement Sbb inside sbbPassivate Method\\n\\n");
	}
	public void sbbRemove() {
		tracer.severe("\n\n\n Announcement Sbb inside sbbRemove Method\\n\\n");
	}
	public void sbbLoad() {
		tracer.severe("\n\n\n Announcement Sbb inside sbbLoad Method\\n\\n");
	}
	public void sbbStore() {
		tracer.severe("\n\n\n Announcement Sbb inside sbbStrore Method\\n\\n");
	}
	public void sbbExceptionThrown(Exception exception, Object event, ActivityContextInterface activity) {
		
		tracer.severe("\n\n\n Announcement Sbb inside sbbExceptionThrown Method");	}
	public void sbbRolledBack(RolledBackContext context) {
		tracer.severe("\n\n\n Announcement Sbb inside sbbRolledBack Method");
	}


	public void onCREATE_CONNECTION_RESPONSE(jain.protocol.ip.mgcp.message.CreateConnectionResponse event, ActivityContextInterface aci/*, EventContext eventContext*/) {
		tracer.severe("\n\n\n Announcement SBB , inside onCREATE_CONNECTION_RESPONSE\n\n\n");

		ServerTransaction txn = getServerTransaction();
		Request request = txn.getRequest();

		ReturnCode status = event.getReturnCode();

		switch (status.getValue()) {
		case ReturnCode.TRANSACTION_EXECUTED_NORMALLY:

			this.setEndpointName(event.getSpecificEndpointIdentifier().getLocalEndpointName());
			tracer.info("\n\n inside onCREATE_CONNECTION_RESPONSE: TRANSACTION_EXECUTED_NORMALLY with endpoint: " + this.getEndpointName()+".....\n\n");

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
			tracer.severe("\n\n\n localaddress="+localAddress+"\n\n");
			tracer.severe("\n\n\n localPort="+localAddress+"\n\n");
			Address contactAddress = null;
			try {
				contactAddress = addressFactory.createAddress("sip:" + localAddress + ":" + localPort);
			} catch (ParseException ex) {
			}
			ContactHeader contact = headerFactory.createContactHeader(contactAddress);

			sendRQNT(WELCOME, true);

			Response response = null;
			try {
				response = messageFactory.createResponse(Response.OK, request, contentType, sdp.getBytes());
				tracer.severe("\n\n\n create Ok response..................response="+response+"\n\n\n");
			} catch (ParseException ex) {
				tracer.severe("\n\n inside onCREATE_CONNECTION_RESPONSE: ParseException while trying to create OK Response\n", ex);
			}

			response.setHeader(contact);
			try {
				tracer.warning("\n\n\n inside onCREATE_CONNECTION_RESPONSE: sending OK message to call Agent.....\n\n");
				txn.sendResponse(response);
			} catch (InvalidArgumentException ex) {
				tracer.severe("\\n\\n inside onCREATE_CONNECTION_RESPONSE: SipException while trying to send OK message to call Agent, InvalidArgumentException while trying to send OK Response", ex);
			} catch (SipException ex) {
				tracer.severe("\\\\n\\\\n inside onCREATE_CONNECTION_RESPONSE: SipException while trying to send OK Response", ex);
			}
			break;
		default:
			try {
				tracer.info("\n\n inside onCREATE_CONNECTION_RESPONSE: sending SERVER_INTERNAL_ERROR to call agent...\n\n");
				response = messageFactory.createResponse(Response.SERVER_INTERNAL_ERROR, request);
				txn.sendResponse(response);
			} catch (Exception ex) {
				tracer.severe("Exception while trying to send SERVER_INTERNAL_ERROR Response", ex);
			}
		}
	}

	public void onNOTIFICATION_REQUEST_RESPONSE(jain.protocol.ip.mgcp.message.NotificationRequestResponse event, ActivityContextInterface aci/*, EventContext eventContext*/) {
		tracer.info("\n\n inside onNotificationRequestResponse : receiving notificationRequest Response.......\n\n");

		ReturnCode status = event.getReturnCode();

		switch (status.getValue()) {
		case ReturnCode.TRANSACTION_EXECUTED_NORMALLY:
			tracer.info("\\n\\n inside onNotificationRequestResponse : The Announcement should have been started.....\n\n");
			break;
		default:
			ReturnCode rc = event.getReturnCode();
			tracer.severe("\n\n inside onNotificationRequestResponse : RQNT failed. Value = " + rc.getValue() + " Comment = " + rc.getComment()+"\n\n");
			EndpointIdentifier endpointID = new EndpointIdentifier(this.getEndpointName(), JBOSS_BIND_ADDRESS + ":"
					+ MGCP_PEER_PORT);
			DeleteConnection deleteConnection = new DeleteConnection(this, endpointID);

			deleteConnection.setTransactionHandle(mgcpProvider.getUniqueTransactionHandler());
			mgcpProvider.sendMgcpEvents(new JainMgcpEvent[] { deleteConnection });
			// TODO : Send DLCX to MMS. Send BYE to UA
			break;
		}
	}

	public void onNOTIFY(jain.protocol.ip.mgcp.message.Notify event, ActivityContextInterface aci/*, EventContext eventContext*/) {
		tracer.info("\n\n inside onNotifymethod :recieving NotifyRequest from media server :\n"+event+"\n\n");

		NotifyResponse response = new  NotifyResponse(event.getSource(),
				ReturnCode.Transaction_Executed_Normally);
		response.setTransactionHandle(event.getTransactionHandle());
		tracer.info("\n\n inside onNotifymethod : sending NotifyResponse with returnCode=Transaction_Executed_Normally........\n\n");
		mgcpProvider.sendMgcpEvents(new JainMgcpEvent[] { response });

		EventName[] observedEvents = event.getObservedEvents();

		for (EventName observedEvent : observedEvents) {
			
			switch (observedEvent.getEventIdentifier().intValue()) {
			case MgcpEvent.REPORT_ON_COMPLETION:
				tracer.info("\n\n inside onNotifymethod : Announcemnet Completed NTFY received...\n\n");
				break;
			case MgcpEvent.REPORT_FAILURE:
				tracer.info("\n\n inside onNotifymethod : Announcemnet Failed received.....\n\n");
				// TODO : Send DLCX and Send BYE to UA
				break;
			default:
				tracer.info("\n\n inside onNotifymethod : MGCP RI expects D/dtmfX, but correct is D/X ... hence it fails to match on "
						+ "MgcpEvent.DTMF_X .... Thus event ID is wrong....");
				//MGCP RI expects D/dtmfX, but correct is D/X ... hence it fails to match on 
				//MgcpEvent.DTMF_X .... Thus event ID is wrong....
//				if(observedEvent.getPackageName().toString().equals("D"))
//				{
//					int decodedId = decodeDTMF(observedEvent);
//					processDTMF(decodedId);
//				}
//				
			
			}
		}
	}

	// 'connectionIdentifier' CMP field setter
	public abstract void setConnectionIdentifier(String value);

	// 'connectionIdentifier' CMP field getter
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
