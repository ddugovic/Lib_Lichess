package org.chernovia.lichess;

import java.net.HttpCookie;
import java.net.URI;
import java.util.List;
import java.util.WeakHashMap;
import java.util.concurrent.TimeUnit;

import org.chernovia.lib.net.zugclient.WebSock;
import org.chernovia.lib.net.zugclient.WebSockListener;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.websocket.client.ClientUpgradeRequest;
import org.eclipse.jetty.websocket.client.WebSocketClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

abstract public class LiClient implements WebSockListener {
    static private WebSocketClient client;
    static String CLIENT = "ZugClient";
	static final Logger LOG = Log.getLogger(LiClient.class);
	public static String LICHESS_ADDR = "lichess.org"; 
	//public static String LICHESS_ADDR = "listage.ovh";
	
    public class LiThread extends Thread {
        protected WebSock sock;

        public LiThread(WebSock s, URI uri) { 
        	sock = s;  setName("Thread: " + uri.toASCIIString()); 
            client = new WebSocketClient(new SslContextFactory(true));
            try { 
            	client.start(); 
            	client.connect(sock,uri,upgradeReq).get();
            } 
            catch (Exception e) { e.printStackTrace(); }
        }
    	public void run() {
    		try { idleLoop(); }
        	catch (Exception e) { LOG.warn(e); try { Thread.sleep(1000); } catch (InterruptedException ignore) {} }
        	sock.end();
        	LOG.info("Exiting thread: " + getName());
        	//try { client.stop(); } catch (Exception e) { e.printStackTrace(); }
    	}
    	public WebSock getSock() { return sock; }
        public void idleLoop() throws InterruptedException {
       		while (sock.isConnected()) {
    			sock.send("{\"t\":\"p\"}"); //,\"v\":9999999}");
    			Thread.sleep(2000); 
       		}
        }
    }
    
    class GameThread extends LiThread {
    	private String gid;
    	public GameThread(WebSock s, URI uri, String i) { super(s,uri); gid = new String(i); }
    	public void run() { super.run(); games.remove(gid); }
    }
    
    class ChallengeThread extends LiThread {
    	String opponent; String gid;
    	public ChallengeThread(WebSock s, URI uri, String opp, String id) { super(s,uri); opponent = opp; gid = id; }
    	public void run() { super.run(); outgoing_challenges.remove(gid); }
    	public void idleLoop() throws InterruptedException {
    		while(sock.isConnecting() || sock.isConnected()) {
    			sock.send(lichessDatagram("challenge",opponent).toString(),false);
				sleep(1250);
			}
    	}
    }
	
    ObjectMapper mapper = new ObjectMapper();
    private HttpClient httpClient;
    private ClientUpgradeRequest upgradeReq;
    private List<HttpCookie> cookies;
    private WeakHashMap<String,GameThread> games;
	private WeakHashMap<String,JsonNode> incomingChallenges;
    private WeakHashMap<String,ChallengeThread> outgoing_challenges;
    private String sri_tag;
    public String username;
    public LiThread main_thread;
    
    public void init() throws Exception {
    	httpClient = new HttpClient(new SslContextFactory(true)); httpClient.start(); 
        //LOG.info("Initialized");
    }
    
    public LiClient(String loc, String user, String pwd) throws Exception {
    	init();
    	outgoing_challenges = new WeakHashMap<String,ChallengeThread>();
		incomingChallenges = new WeakHashMap<String,JsonNode>();
    	games = new WeakHashMap<String,GameThread>();
    	username = user;
    	sri_tag = "?sri=" + (" " + Math.random()).substring(3,12); // + "&version=0";
    	URI uri = URI.create(loc + sri_tag); LOG.info("Login URI:" + uri);
    	upgradeReq = login(username,pwd,uri); 
    	WebSock sock = new WebSock("Lichess_" + username); 
    	sock.addListener(this); main_thread = new LiThread(sock,uri); main_thread.start();
    }
    
    private ClientUpgradeRequest login(String user, String pwd, URI uri) throws Exception {
		ContentResponse response = httpClient.newRequest("https://" + LICHESS_ADDR + "/login")
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
        r.setHeader("Origin","https://" + LICHESS_ADDR);
        r.setHeader("User Agent",CLIENT);
        r.setCookies(cookies);
        return r;
    }
    
    public WebSock startGame(String gid, WebSockListener l) throws Exception {
    	URI game_uri = 
		URI.create("wss://socket." + LICHESS_ADDR + "/" + gid + "/socket/v2" + sri_tag); 
    	WebSock sock = new WebSock("Chessgame: " + gid); sock.addListener(l);
		GameThread newgame = new GameThread(sock,game_uri,gid);
		games.put(gid,newgame); 
		newgame.start();
		return newgame.getSock();
    }
    
    public JsonNode finger() {
    	try {
    		ContentResponse response = httpClient.newRequest("https://" + LICHESS_ADDR + "/account/info")
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
    		ContentResponse response = httpClient.newRequest("https://" + LICHESS_ADDR + "/" + gid)
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
    		ContentResponse response = httpClient.newRequest("https://" + LICHESS_ADDR + "/challenge/" + id)
				.header("Accept","application/vnd.lichess.v2+json")
				.cookie(cookies.get(0))
				.method("GET")
		        .timeout(5, TimeUnit.SECONDS)
		 		.send();
			return mapper.readTree(response.getContentAsString());
		} catch (Exception e) { e.printStackTrace(); return null; } 
    }
    
    public String createChallenge(String opponent, int var, boolean clock, int time, int inc, String color) {
    	String loc = "https://" + LICHESS_ADDR + "/setup/friend?user=" + opponent.toLowerCase() + "&variant=" + var + 
    	"&clock=" + clock + "&time=" + time + "&increment=" + inc + "&timeMode=1&days=1&color=" + color; 
    	try {
    		ContentResponse response = httpClient.newRequest(loc)
    			.header("Accept","application/vnd.lichess.v2+json")
				.cookie(cookies.get(0))
				.method("POST")
		        .timeout(5, TimeUnit.SECONDS)
		 		.send();
			JsonNode challenge = mapper.readTree(response.getContentAsString());
    		LOG.info("Challenge created:" + challenge.toString());
    		String opp = challenge.get("challenge").get("destUser").get("id").asText();
			String gid = challenge.get("challenge").get("id").asText();
			if (!outgoing_challenges.containsKey(gid)) { 
				//wss://socket.lichess.org:9026/challenge/qIPZtvmg/socket/v2?sri=3jubk0ssxm
		    	URI challenge_uri = URI.create("wss://socket." + LICHESS_ADDR + "/challenge/" +	gid + 
		    	"/socket/v2?sri=1234&version=0");
		    	WebSock sock = new WebSock("Challenge: " + username + " -> " + opponent);
		    	ChallengeThread newChallenge = new ChallengeThread(sock,challenge_uri,opp,gid);
		    	outgoing_challenges.put(gid,newChallenge);
		    	newChallenge.start();
		    	return gid;
			}
		} 
    	catch (Exception e) { LOG.warn("Challenge Creation Error:" + e.getMessage()); } // e.printStackTrace(); }
    	return null;
    }
    
    public JsonNode acceptChallenge(String gid) {
    	LOG.info("Accepting Challenge as " + username + ": " + gid);
    	try {
    		ContentResponse response = httpClient.newRequest("https://" + LICHESS_ADDR + "/challenge/" + gid + "/accept")
				.header("Accept","application/vnd.lichess.v2+json")
				.cookie(cookies.get(0))
				.method("POST")
		        .timeout(5, TimeUnit.SECONDS)
		 		.send();
    		LOG.info("Challenge accept response: " + response.getContentAsString());
			return mapper.readTree(response.getContentAsString());
		} catch (Exception e) { e.printStackTrace(); return null; } 
    }
    
	public void clearIncomingChallenges() { incomingChallenges.clear(); }
	public void addChallenge(String gid, JsonNode challenge) { incomingChallenges.put(gid,challenge); }
	public WeakHashMap<String,JsonNode> getIncomingChallenges() { return incomingChallenges; }
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

}
