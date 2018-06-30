# Call-Blocking



## Design Overview  

We have implemented a Call blocking service application that requires the usage of both MGCP and SIP protocols. The application consists of a third party call control based on a central back-to-back UA (B2BUA), where the caller (UAC) perform a call towards a callee (UAS). Depending on the callee has blacklist or whitelist containing the caller or not, the call will be forwarded or ended with played announcement.  

**BlackList:** numbers that you wish to block, while allowing the rest of the calls to reach you. You can activate it all the day or on a specific time frame.  
**WhileList:** numbers that are allowed to reach you, while appearing unavailable to the rest of the callers. You can activate it all the day or on specific time 				   frame.  

**Note:** If a conflict happens between the time frame of blacklist and white list, the priority will go to the whitelist and will be activated.
The call blocking control unit is implemented by a Restcomm JSLEE which acts as an Application Server (AS) implementing both the SIP B2BUA and the MGCP CA.

#### There are five main signaling entities are involved:  

**1.SIP UAC:** which represents caller (PartyA).  
**2.Call Screening Server:** act as SIP Proxy Server and MGCP Call Agent.  
**3.Media Gateway Server.**  
**4.SIP UAS:** which represents callee (PartyB).  
**5.Database:** to store Users profiles.  


#### Signaling flow  

It is started by the SIP UAC, which sends a SIP INVITE message towards the Application Server (AS). 
When this INVITE is received, the AS event routing subsystem is invoked and a Selector root SBB is created. It immediately queries the DB in order to retrieve the partyB profile. 
Now the Selector SBB has all the information needed and at this point there are two Scenarios  can be happened:  

**Scenario 1:**  

If the partyB profile has a blacklist containing partyA, the selector SBB will create child sbb (Annoucement-SBB) which handle a connection with media server and send NOTIFYREQUEST with to make media server play announcement over RTP. After announcement is completed the media server will send NOTIFY to AS and the AS will terminate call by sending BYE message to the caller.

![Alt text](https://github.com/msrashed2018/Call-Blocking/blob/master/img/scenario1.png?raw=true "scenario1")  

**Scenario 2:**  
If the partyB profile has a whitelist containing the caller (PartyA), the selector SBB will create child SBB (B2BUA-SBB) which setup call between partyA (UAC) and partyB UAS using SIP B2BUA implementation.

![Alt text](https://github.com/msrashed2018/Call-Blocking/blob/master/img/scenario2.png?raw=true "scenario1")  













### Setup  

**All needed binaries and libraries are available in the following links:**  
	1- https://github.com/msrashed2018/Call-Blocking/tree/master/lib  
	2- https://github.com/msrashed2018/Call-Blocking/releases   


#### Pre-Install Requirements and Prerequisites  
Ensure that the following requirements have been met before continuing with the install.

#### Hardware Requirements  
The Application doesn't change the Restcomm JAIN SLEE Hardware Requirements, refer to Restcomm JAIN SLEE documentation for more information.  

#### Software Prerequisites  
**A. Restcomm slee  wildfly Server Container**  
The application requires Restcomm JAIN SLEE properly set, with following list of dependencies deployed/started.  
	**1.MGCP RA**
	It is required that MGCP RA is deployed. The MGCP RA is responsible to fire the MGCP Events corresponding to MGCP Request/Response received from Media Gateway 	    	server.  
	**2.SIP11 RA**  
	It is required that SIP11 RA is deployed. The SIP RA is responsible to fire the SIP Events like INVITE, BYE etc received from SIP User Agents  
	**3.JDBC RA**  
	It is required that JDBC RA is deployed. The JDBC RA is responsible to receive the JDBC Events like Jdbc Task Execution Throwable Event and Simple Jdbc Task Result 	Event.  
	**4.Postgresql driver**    
	It is required that postgresql driver is deployed. The postgresql is responsible to interact with wildfly server datasource.    
 
**B. Restcomm Media Server:**  
The application sends MGCP Signals to Media Gateway (Media Server) to play announcements and also requests ONCOMPLETE and ONFAILURE events to be notified back to Application. The media part is taken care by Restcomm Media Server; it is required that Restcomm Media Server is started before the User dials respective digits to application.

**C. Postgresql Database Server:**  
The application sends JDBC queries to Postgresql database server to get information about SIP users like address, blacklist and white list numbers. It is required that Postgresql Database Server is started before the User dials respective digits to application.

**D. SIP Phone:**  
Any sip phone can be used to making VOIP call but make sure that it support voice codecs as same as  media server gateway support. We have used Linphone and SimpleSip soft phone for our demo and you can download it for free.


## Running the Project  




### Steps  

**1.Ensure that postgresql service is running and database is created**   

	Database commands file : callblocking/data/callblocking-database-commands.sql

**2.Restcomm Media Server Configuration:**  

	a. Download restcomm media server 5.0.0.2 and unzip package
	b. Change IP bind address in file (Path: <install directory>/conf/ mediaserver.xml).
	c. Run the server 
		$ cd <install directory>/bin
		$./run.sh

**3.Restcomm SLEE wildfly configuration** 

	a. Deploy the following packages in server deployments directory (Path: <install directory>/wildfly-10.1.0.Final/standalone/deployments/)

		i. SIP RA : change bind IP Address (deactivate resource adaptor before changing IP bind address parameter then activate it again)    
		ii. MGCP RA : change bind IP Address (deactivate resource adaptor before changing IP bind address parameter then activate it again)  
		iii. JDBC RA  
		iv. Postgresql driver: (it used by wildfly datasource to interact with postgresql database server).  
		v. Announcement war file (it is web application contains an audio file which will be used by media server to be played on RTP channel between sip client and 			media server).  

	b. Start the server
		$ cd <install directory>/wildfly-10.1.0.Final/bin
		$./ standalone.sh –b=192.168.1.5 		 (type your machine ip address  instead)
	c. Adjust wildfly datasource 
	d. Deploy project jar file (Callblocking-1.0-final.jar) and start call blocking service 

**4. Download CSipSimple ( https://play.google.com/store/apps/details?id=com.csipsimple&hl=en ) on android phone. We used CSipSimple phone instead of linphone as it support audio codec that media server can decode it. So it will work in scenario of playing annoucement.**  

**5. Download linphone ( http://www.linphone.org ) on windows or centos machine.  Change username to be equal to any msisdn as you define in users profile in your database. You can use linphone for B2BUA scenario. It works well.**  

**6. Add users to database with their IP addresses so Application server can forward call.**  

**7. Insert users in black list and white list.**  

**8. Try to make two call, one for user exists in white list and another for user exists in black list as :**  
		SIP URL Format is written like:      
 			sip:[partyBmsisdn]@[application server Ip address]:5060  
	

 

