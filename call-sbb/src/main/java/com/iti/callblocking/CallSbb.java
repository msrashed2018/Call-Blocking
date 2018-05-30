package com.iti.callblocking;

import javax.sip.RequestEvent;

import javax.sip.address.AddressFactory;
import javax.sip.address.URI;

import javax.sip.header.FromHeader;

import javax.sip.message.Request;
import javax.sip.message.Response;
import javax.slee.facilities.Tracer;

import org.restcomm.slee.resource.jdbc.JdbcActivityContextInterfaceFactory;
import org.restcomm.slee.resource.jdbc.JdbcResourceAdaptorSbbInterface;
import org.restcomm.slee.resource.jdbc.task.simple.SimpleJdbcTaskResultEvent;

import javax.slee.*;

import net.java.slee.resource.sip.SleeSipProvider;

import java.text.ParseException;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;

import org.mobicents.slee.SbbContextExt;
import org.restcomm.slee.resource.jdbc.JdbcActivity;

import com.iti.callblocking.database.CallBlockingDBMS;
import com.iti.callblocking.events.CustomEvent;

public abstract class CallSbb implements Sbb, Call {
	private SbbContextExt contextExt;
	private JdbcActivityContextInterfaceFactory jdbcACIF;
	private JdbcResourceAdaptorSbbInterface jdbcRA;
	private CallBlockingDBMS callBlockingDB;
	private Tracer tracer;

	ActivityContextInterface sipACI;
	int Bparty;
	RequestEvent inviteEvent;
	private SleeSipProvider sipProvider;
	private AddressFactory addressFactory;

	private SbbContext sbbContext; // This SBB's SbbContext
	// TODO: Perform further operations if required in these methods.

	public void setSbbContext(SbbContext context) {

		this.sbbContext = context;
		this.contextExt = (SbbContextExt) context;
		if (tracer == null) {
			tracer = sbbContext.getTracer(CallSbb.class.getSimpleName());
		}
		// initialize JDBC API
		this.jdbcRA = (JdbcResourceAdaptorSbbInterface) contextExt
				.getResourceAdaptorInterface(JdbcResourceAdaptorSbbInterface.RATYPE_ID, "JDBCRA");
		this.jdbcACIF = (JdbcActivityContextInterfaceFactory) contextExt
				.getActivityContextInterfaceFactory(JdbcActivityContextInterfaceFactory.RATYPE_ID);
		// initialize SIP API
		try {
			final Context ctx = (Context) new InitialContext().lookup("java:comp/env");
			sipProvider = (SleeSipProvider) ctx.lookup("slee/resources/jainsip/1.2/provider");
			addressFactory = sipProvider.getAddressFactory();
		} catch (NamingException e) {
			tracer.severe(e.getMessage());
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

	public void forwardToAnnouncementSbb(ActivityContextInterface aci) {
		ChildRelation relation = getAnnouncementSbbChild();
		SbbLocalObject child;
		try {
			child = relation.create();
			aci.attach(child);
			aci.detach(sbbContext.getSbbLocalObject());
			CustomEvent customEvent = new CustomEvent(inviteEvent);
			fireCallCreated(customEvent, aci, null);
		} catch (TransactionRequiredLocalException e) {
			tracer.severe("\n\n TransactionRequiredLocalException", e);
		} catch (SLEEException e) {
			tracer.severe("\n\n SLEEException", e);
		} catch (CreateException e) {
			tracer.severe("\n\n CreateException", e);
		}
	}

	public void forwardToB2buaSbb(ActivityContextInterface aci, URI bPartyUri) {
		ChildRelation relation = getB2buaSbbChild();
		SbbLocalObject child;
		try {
			child = relation.create();
			aci.attach(child);
			aci.detach(sbbContext.getSbbLocalObject());
			CustomEvent customEvent = new CustomEvent(inviteEvent);
			customEvent.setbPartyUri(bPartyUri);
			fireCallCreated(customEvent, aci, null);
		} catch (TransactionRequiredLocalException e) {
			tracer.severe("\n\n TransactionRequiredLocalException", e);
		} catch (SLEEException e) {
			tracer.severe("\n\n SLEEException", e);
		} catch (CreateException e) {
			tracer.severe("\n\n CreateException", e);
		}
	}

	public void onINVITE(javax.sip.RequestEvent event, ActivityContextInterface aci/* , EventContext eventContext */) {
		// ACI is the server transaction activity
		try {
			callBlockingDB = new CallBlockingDBMS();
			// send "trying" response
			sipACI = aci;
			inviteEvent = event;
			replyToRequestEvent(event, Response.TRYING);
			Request request = event.getRequest();
			FromHeader fromHeader = (FromHeader) request.getHeader(FromHeader.NAME);
			String[] partyA = fromHeader.getAddress().getURI().toString().split("@|\\:");
			String[] partyB = request.getRequestURI().toString().split("@|\\:");
			Bparty = Integer.valueOf(partyB[1]);
			// create activity using the RA sbb interface
			JdbcActivity jdbcActivity = jdbcRA.createActivity();
			// get its aci from the RA ACI factory
			ActivityContextInterface jdbcACI = jdbcACIF.getActivityContextInterface(jdbcActivity);
			// attach the sbb entity
			jdbcACI.attach(contextExt.getSbbLocalObject());
			jdbcActivity.execute(callBlockingDB.processParties(partyA[1], Bparty));

		} catch (Throwable e) {
			tracer.severe("\n\n\n Failed to process incoming INVITE.", e);
			replyToRequestEvent(event, Response.SERVICE_UNAVAILABLE);
		}
	}

	public void onSimpleJdbcTaskResultEvent(SimpleJdbcTaskResultEvent event,
			ActivityContextInterface aci/* , EventContext eventContext */) {
		if (event.getResult().equals(true)) {
			if ((callBlockingDB.HasWhiteList && callBlockingDB.IsWhiteTimeFrame && !callBlockingDB.InWhiteList)
					|| (callBlockingDB.HasBlackList && callBlockingDB.IsBlackTimeFrame && callBlockingDB.InBlackList)) {
				forwardToAnnouncementSbb(sipACI);
			} else {
				try {
					URI to = addressFactory.createURI("sip:" + Bparty + "@" + callBlockingDB.address);
					forwardToB2buaSbb(sipACI, to);
				} catch (ParseException e) {
					tracer.severe("ParseException inside onSimpleJdbcTaskResultEvent Method ", e);
				}
			}
		}
	}

	public void onJdbcTaskExecutionThrowableEvent(
			org.restcomm.slee.resource.jdbc.event.JdbcTaskExecutionThrowableEvent event,
			ActivityContextInterface aci/* , EventContext eventContext */) {
		tracer.severe("\nJdbcTaskExecutionThrowableEvent " + event.toString());
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
		tracer.severe("\nsbbExceptionThrown", exception);
	}

	public void sbbRolledBack(RolledBackContext context) {
		tracer.severe("\n\n\n Call Sbb inside sbbRolledBack Method");
	}

	public abstract ChildRelation getAnnouncementSbbChild();

	public abstract ChildRelation getB2buaSbbChild();

	public abstract void fireCallCreated(CustomEvent event, ActivityContextInterface aci, javax.slee.Address address);
}
