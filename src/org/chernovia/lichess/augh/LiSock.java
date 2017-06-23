package org.chernovia.lichess.augh;

import java.net.*;
import java.util.List;
import java.util.Vector;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketClose;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketConnect;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketError;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketMessage;
import org.eclipse.jetty.websocket.api.annotations.WebSocket;
import org.eclipse.jetty.websocket.client.ClientUpgradeRequest;
import org.eclipse.jetty.websocket.client.WebSocketClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@WebSocket
public class LiSock extends Thread {
	
    public static String CLIENT = "ZugClient";
	private static final Logger LOG = Log.getLogger(LiSock.class);
	private Vector<LiWatcher> watchers;
    private URI uri;
    private ClientUpgradeRequest upgradeReq;
    private WebSocketClient webClient;
    private HttpClient httpClient;
    private Session session;
    private String username;
    private String game_id; 
    private JsonNode gameData;
    List<HttpCookie> cookies;
    ObjectMapper mapper = new ObjectMapper();
    
    public boolean logged_in() { return upgradeReq != null; }
    public ClientUpgradeRequest getUpgrade() { return upgradeReq; }
    public String getGameId() { return game_id; }
    public Session getSession() { return session; }
    
    private void init(String user, String loc) {
    	username = user; String sri = (" " + Math.random()).substring(3,12);
    	uri = URI.create(loc + "?sri=" + sri + "&version=0");
    	LOG.info("New uri: " + uri);
    	watchers = new Vector<LiWatcher>();
    	httpClient = new HttpClient(new SslContextFactory(true));
		try { httpClient.start(); } catch (Exception e) { e.printStackTrace(); }
    }
    
    public LiSock(String loc, String user, String pwd) {
    	init(user,loc);
    	try { upgradeReq = login(user,pwd); } catch (Exception e) { LOG.warn(e.getMessage()); upgradeReq = null; }
    }
    
    public LiSock(String gid, String user, String loc, ClientUpgradeRequest req) {
    	init(user,loc); //"wss://socket.lichess.org/" + gid + "/socket/v2" 
    	upgradeReq = req;	cookies = upgradeReq.getCookies();
    	gameData = getGame(gid); game_id = gid;
    }
    
    private ClientUpgradeRequest login(String user, String pwd) throws Exception {
		ContentResponse response = httpClient.newRequest("https://lichess.org/login")
				.header("Accept","application/vnd.lichess.v2+json")
				.param("username",user)
				.param("password",pwd)
				.method("POST")
		        .timeout(5, TimeUnit.SECONDS)
		 		.send();
		LOG.info("UPGRADE:" + response.getContentAsString());
			
		cookies = HttpCookie.parse(response.getHeaders().get("Set-Cookie"));
		for (HttpCookie cookie : cookies) LOG.info("Cookie: " + cookie.toString());
			    
		ClientUpgradeRequest r = new ClientUpgradeRequest();
        r.setRequestURI(uri);
        r.setHeader("Origin","https://lichess.org");
        r.setHeader("User Agent",CLIENT);
        r.setCookies(cookies);
        
        return r;
    }
    
    public JsonNode finger() {
    	try {
    		ContentResponse response = httpClient.newRequest("https://lichess.org/account/info")
				.header("Accept","application/vnd.lichess.v2+json")
				.cookie(cookies.get(0)) //npe due to massive stupidity
				.method("GET")
		        .timeout(5, TimeUnit.SECONDS)
		 		.send();
    		//LOG.info("FINGER:" + response.getContentAsString());
			return mapper.readTree(response.getContentAsString());
		} catch (Exception e) { e.printStackTrace(); return null; } 
    }
    
    public JsonNode getGame() { return gameData; }
    public JsonNode getGame(String gid) {
    	try {
    		ContentResponse response = httpClient.newRequest("https://lichess.org/" + gid)
				.header("Accept","application/vnd.lichess.v2+json")
				.cookie(cookies.get(0))
				.method("GET")
		        .timeout(5, TimeUnit.SECONDS)
		 		.send();
    		//LOG.info("GAME:" + response.getContentAsString());
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
    		//LOG.info("GAME:" + response.getContentAsString());
			return mapper.readTree(response.getContentAsString());
		} catch (Exception e) { e.printStackTrace(); return null; } 
    }
    
    public JsonNode createChallenge(String opponent, int var, boolean clock, int time, int inc, String color) {
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
    		//LOG.info("Challenge:" + response.getContentAsString());
			return mapper.readTree(response.getContentAsString());
		} catch (Exception e) { e.printStackTrace(); return null; } 
    }
    
    public JsonNode acceptChallenge(String gid) {
    	try {
    		ContentResponse response = httpClient.newRequest("https://lichess.org/challenge/" + gid + "/accept")
				.header("Accept","application/vnd.lichess.v2+json")
				.cookie(cookies.get(0))
				.method("POST")
		        .timeout(5, TimeUnit.SECONDS)
		 		.send();
    		//LOG.info("Challenge:" + response.getContentAsString());
			return mapper.readTree(response.getContentAsString());
		} catch (Exception e) { e.printStackTrace(); return null; } 
    }
    
    public void addWatcher(LiWatcher gw) { watchers.add(gw); }
    public void removeWatcher(LiWatcher gw) { watchers.remove(gw); }
    
    public void run() {
    	LOG.info("STARTING: " + username + " -> " + cookies.get(0).toString());
    	try {
	        webClient = new WebSocketClient(new SslContextFactory(true));
    		webClient.start();
    		Future<Session> fut = webClient.connect(this,uri,upgradeReq);
    		session = fut.get();
    		mainLoop();
    	}
    	catch (Exception e) { LOG.warn(e); try { Thread.sleep(1000); } catch (InterruptedException ignore) {} }
    	if (session != null && session.isOpen()) session.close(); 
    	for (LiWatcher gw : watchers) gw.lisock_fin();
    }
    
    public void mainLoop() throws InterruptedException {
   		while (session.isOpen()) {
			Thread.sleep(2000); 
			session.getRemote().sendStringByFuture("{\"t\":\"p\"}"); //,\"v\":9999999}"); //HUGE number for socket version
   		}
    }
    
    public void end() {	if (session !=null) session.close(); interrupt(); }
    
    public void send(String msg) {
    	LOG.info("SENDING: " + msg);
    	session.getRemote().sendStringByFuture(msg);
    }

    @OnWebSocketConnect
    public void onConnect(Session sess) {
        LOG.info("onConnect({})",sess);
    }

    @OnWebSocketClose
    public void onClose(int statusCode, String reason) {
        LOG.info("onClose({}, {})", statusCode, reason);
    }

    @OnWebSocketError
    public void onError(Throwable cause) {
        LOG.warn(cause);
    }

    @OnWebSocketMessage
    public void onMessage(String msg) {
        LOG.info("onMessage() - {}", msg);
        for (LiWatcher gw : watchers) gw.lisock_msg(msg);
    }
}

