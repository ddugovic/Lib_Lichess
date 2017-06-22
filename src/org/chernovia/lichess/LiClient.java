package org.chernovia.lichess;

import java.net.HttpCookie;
import java.net.URI;
import java.util.List;
import java.util.WeakHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.chernovia.lib.net.zugclient.WebSock;
import org.chernovia.lib.net.zugclient.WebSockListener;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.client.ClientUpgradeRequest;
import org.eclipse.jetty.websocket.client.WebSocketClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

abstract public class LiClient implements WebSockListener {
	
    public class LiThread extends Thread {
        protected WebSock sock;
        public LiThread(WebSock s) { sock = s;  setName("Thread: " + sock); }
    	public void run() {
    		try { idleLoop(); }
        	catch (Exception e) { LOG.warn(e); try { Thread.sleep(1000); } catch (InterruptedException ignore) {} }
        	sock.end();
        	LOG.info("Exiting thread: " + getName());
    	}
    	public WebSock getSock() { return sock; }
        public void idleLoop() throws InterruptedException {
       		while (sock.isConnecting() || sock.isConnected()) {
    			sock.send("{\"t\":\"p\",\"v\":9999999}");
    			Thread.sleep(2000); 
       		}
        }
    }
    
    class GameThread extends LiThread {
    	private String gid;
    	public GameThread(WebSock s, String i) { super(s); gid = new String(i); }
    	public void run() { super.run(); games.remove(gid); }
    }
    
    class ChallengeThread extends LiThread {
    	String opponent; String gid;
    	public ChallengeThread(WebSock s, String opp, String id) { super(s); opponent = opp; gid = id; }
    	public void run() { super.run(); outgoing_challenges.remove(gid); }
    	public void idleLoop() throws InterruptedException {
    		while(sock.isConnecting() || sock.isConnected()) {
    			String msg = lichessDatagram("challenge",opponent).toString(); //LOG.info("Sending: " + msg);
				sock.send(msg);
				sleep(1250);
			}
    	}
    }
	
    ObjectMapper mapper = new ObjectMapper();
    static String CLIENT = "ZugClient";
	private static final Logger LOG = Log.getLogger(LiClient.class);
    private static WebSocketClient client;
    private static HttpClient httpClient;
    private ClientUpgradeRequest upgradeReq;
    private List<HttpCookie> cookies;
    private WeakHashMap<String,GameThread> games;
    private WeakHashMap<String,ChallengeThread> outgoing_challenges;
    public LiThread main_thread;
    public String username;
    
    public static void init() throws Exception {
    	httpClient = new HttpClient(new SslContextFactory(true));
		httpClient.start(); 
    	SslContextFactory sslContextFactory = new SslContextFactory();
        sslContextFactory.setTrustAll(true); //magic!
        client = new WebSocketClient(sslContextFactory);
        client.start();
        LOG.info("Initialized");
    }
    
    public LiClient(String loc, String user, String pwd) throws Exception {
    	//init();
    	username = user;
    	URI uri = URI.create(loc + rnd_uri_tag());
    	LOG.info("URI:" + uri);
    	upgradeReq = login(username,pwd,uri); 
    	outgoing_challenges = new WeakHashMap<String,ChallengeThread>();
    	WebSock sock = new WebSock("Lichess_" + username); 
    	Future<Session> fut = client.connect(sock,uri,upgradeReq); 
    	if (fut == null || fut.get() == null) { LOG.info("Error: no connection"); }
    	sock.addListener(this); main_thread = new LiThread(sock); main_thread.start();
    }
    
    private ClientUpgradeRequest login(String user, String pwd, URI uri) throws Exception {
		ContentResponse response = httpClient.newRequest("https://lichess.org/login")
				.header("Accept","application/vnd.lichess.v2+json")
				.param("username",user)
				.param("password",pwd)
				.method("POST")
		        .timeout(5, TimeUnit.SECONDS)
		 		.send();
		cookies = HttpCookie.parse(response.getHeaders().get("Set-Cookie"));
		//LOG.info("Response: " + response.getContentAsString());	    
		ClientUpgradeRequest r = new ClientUpgradeRequest();
        r.setRequestURI(uri);
        r.setHeader("Origin","https://lichess.org");
        r.setHeader("User Agent",CLIENT);
        r.setCookies(cookies);
        return r;
    }
    
    public WebSock startGame(String gid, WebSockListener l) throws Exception {
    	URI game_uri = 
		URI.create("wss://socket.lichess.org/" + gid + "/socket/v2?sri=zug" + (int)(Math.random() * 999)); 
    	WebSock sock = new WebSock("Chessgame: " + gid); sock.addListener(l);
		client.connect(sock,game_uri,upgradeReq); 
		GameThread newgame = new GameThread(sock,gid);
		games.put(gid,newgame); 
		newgame.start();
		return newgame.getSock();
    }
    
    public JsonNode finger() {
    	try {
    		ContentResponse response = httpClient.newRequest("https://lichess.org/account/info")
				.header("Accept","application/vnd.lichess.v2+json")
				.cookie(cookies.get(0))
				.method("GET")
		        .timeout(5, TimeUnit.SECONDS)
		 		.send();
			return mapper.readTree(response.getContentAsString());
		} catch (Exception e) { e.printStackTrace(); return null; } 
    }
    
    public JsonNode getGame(String gid) {
    	try {
    		ContentResponse response = httpClient.newRequest("https://lichess.org/" + gid)
				.header("Accept","application/vnd.lichess.v2+json")
				.cookie(cookies.get(0))
				.method("GET")
		        .timeout(5, TimeUnit.SECONDS)
		 		.send();
			return mapper.readTree(response.getContentAsString());
		} catch (Exception e) { e.printStackTrace(); return null; } 
    }
    
    public JsonNode getChallenge(String id) {
    	try {
    		ContentResponse response = httpClient.newRequest("https://lichess.org/challenge/" + id)
				.header("Accept","application/vnd.lichess.v2+json")
				.cookie(cookies.get(0))
				.method("GET")
		        .timeout(5, TimeUnit.SECONDS)
		 		.send();
			return mapper.readTree(response.getContentAsString());
		} catch (Exception e) { e.printStackTrace(); return null; } 
    }
    
    public void createChallenge(String opponent, int var, boolean clock, int time, int inc, String color) {
    	String loc = "https://lichess.org/setup/friend?user=" + opponent.toLowerCase() + "&variant=" + var + 
    	"&clock=" + clock + "&time=" + time + "&increment=" + inc + "&timeMode=1&days=1&color=" + color; 
   	   	LOG.info("Creating challenge: " + loc);
    	try {
    		ContentResponse response = httpClient.newRequest(loc)
    			.header("Accept","application/vnd.lichess.v2+json")
				.cookie(cookies.get(0))
				.method("POST")
		        .timeout(5, TimeUnit.SECONDS)
		 		.send();
    		LOG.info("Challenge:" + response.getContentAsString());
			JsonNode challenge = mapper.readTree(response.getContentAsString());
			String gid = challenge.get("challenge").get("id").asText();
			if (!outgoing_challenges.containsKey(gid)) { //startChallenge(id,opponent);
		    	URI challenge_uri = 
    			URI.create("wss://socket.lichess.org/challenge/" + gid + "/socket/v2" + rnd_uri_tag());
		    	WebSock sock = new WebSock("Challenge: " + username + " -> " + opponent);
		    	client.connect(sock,challenge_uri,upgradeReq);
		    	ChallengeThread newChallenge = new ChallengeThread(sock,opponent,gid);
		    	outgoing_challenges.put(gid,newChallenge);
		    	newChallenge.start();
			}
		} 
    	catch (Exception e) { e.printStackTrace(); } 
    }
    
    public JsonNode acceptChallenge(String gid) {
    	try {
    		ContentResponse response = httpClient.newRequest("https://lichess.org/challenge/" + gid + "/accept")
				.header("Accept","application/vnd.lichess.v2+json")
				.cookie(cookies.get(0))
				.method("POST")
		        .timeout(5, TimeUnit.SECONDS)
		 		.send();
			return mapper.readTree(response.getContentAsString());
		} catch (Exception e) { e.printStackTrace(); return null; } 
    }
    
	public void clearOutgoingChallenges() {
		for (ChallengeThread thread : outgoing_challenges.values()) thread.sock.end();
		outgoing_challenges.clear();
	}
    
	public JsonNode lichessDatagram(String type, String data) {
		ObjectNode obj = mapper.createObjectNode();
		obj.put("t", type);
		obj.put("d", data);
		return obj;
	}

    public static String rnd_uri_tag() {
    	return "?sri=" + (" " + Math.random()).substring(3,12) + "&version=0";
    }
    

}
