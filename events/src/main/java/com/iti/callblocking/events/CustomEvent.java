package com.iti.callblocking.events;

import java.util.Random;

import javax.sip.RequestEvent;
import javax.sip.address.URI;

import java.io.Serializable;

public final class CustomEvent implements Serializable {
	private RequestEvent InviteEvent;
	private byte[] sdp;
	private URI bPartyUri;
	public CustomEvent(RequestEvent InviteEvent) {
		id = new Random().nextLong() ^ System.currentTimeMillis();
//		this.sdp=sdp;
		this.InviteEvent=InviteEvent;
	}

	public boolean equals(Object o) {
		if (o == this) return true;
		if (o == null) return false;
		return (o instanceof CustomEvent) && ((CustomEvent)o).id == id;
	}
	
	public int hashCode() {
		return (int) id;
	}
	
	public String toString() {
		return "CustomEvent[" + hashCode() + "]";
	}
	public byte[] getSdp() {
		return sdp;
		
	}
	public void setSdp(byte[] sdp) {
		this.sdp=sdp;
		
	}

	public URI getbPartyUri() {
		return bPartyUri;
	}

	public void setbPartyUri(URI bPartyUri) {
		this.bPartyUri = bPartyUri;
	}

	public RequestEvent getInviteEvent() {
		return InviteEvent;
		
	}
	public void setInviteEventnt(RequestEvent InviteEvent) {
		this.InviteEvent=InviteEvent;
		
	}
	private final long id;
}
