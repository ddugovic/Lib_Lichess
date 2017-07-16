package org.chernovia.lichess.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;

import org.chernovia.lichess.gson.GameData;
import org.chernovia.lichess.gson.LichessUser;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import sun.net.www.protocol.http.HttpURLConnection;
import com.google.gson.Gson;

public class LichessUtils {
	
	public static String cr = "\n";
	
	public static LichessUser getUser(String user) {
		try {
			URL url = new URL("http://lichess.org/api/user/" + user);
			HttpURLConnection con = (HttpURLConnection) url.openConnection();
			con.setRequestMethod("GET");
			StringBuffer responseData = new StringBuffer();
			InputStreamReader in = new InputStreamReader((InputStream)con.getContent());
			BufferedReader br = new BufferedReader(in);
			String line; 
			do {
			    line = br.readLine();
			    if (line != null) { responseData.append(line); }
			} while (line != null);
			//System.out.println(responseData);
			Gson g = new Gson();
			return g.fromJson(responseData.toString(), LichessUser.class);
		}
		catch (Exception augh) { augh.printStackTrace(); return null; }
	}
	
	public static GameData getGame(String gid) { 
		return getGame(gid, "?with_moves=1&with_fens=1&with_opening=1");
	}
	public static GameData getGame(String gid, String extras) {
		//BUG: lichess API is broken, no longer provides FENs from ongoing games
		//System.out.println("Fetching Game ID: " + gid);
		try {
			URL url = new URL("http://lichess.org/api/game/" + gid + extras);
			HttpURLConnection con = (HttpURLConnection) url.openConnection();
			con.setRequestMethod("GET");
			StringBuffer responseData = new StringBuffer();
			InputStreamReader in = new InputStreamReader((InputStream)con.getContent());
			BufferedReader br = new BufferedReader(in);
			String line; 
			do {
			    line = br.readLine();
			    if (line != null) { responseData.append(line); }
			} while (line != null);
			//System.out.println(responseData);
			Gson g = new Gson();
			return g.fromJson(responseData.toString(), GameData.class);
		}
		catch (Exception augh) { augh.printStackTrace(); return null; }
	}
	
	public static String getGameJSON(String gid) {
		try {
			return Jsoup.connect(
			"https://lichess.org/"+gid).ignoreContentType(true).header(
			"Content-Type","application/x-www-form-urlencoded").header(
			"Accept","application/vnd.lichess.v2+json").validateTLSCertificates(false).
			get().body().text(); 
		} catch (IOException e) { e.printStackTrace(); return null; }	
	}
	
	public static String[] getTVData(String gameType) {
		Document doc;
		try {
			doc = Jsoup.connect(
			"https://lichess.org/games/" + gameType).ignoreContentType(false).header(
			"Content-Type","application/xhtml+xml").validateTLSCertificates(false).get();
			//System.out.println(doc);
			Elements gameIDs = doc.select("[data-live]");
			String[] data = new String[gameIDs.size()]; int i=0;
			for (Element e: gameIDs) {
				//System.out.println("Attribute: " + e.attr("data-live"));
				data[i++] = e.attr("data-live");
			}
			return data;
		} catch (IOException e) { e.printStackTrace(); return null; }
	} 
	
	
	public static String getUserData(String user,String id) {
		Document doc;
		try {
			doc = Jsoup.connect(
			"https://lichess.org/api/user/" + user).ignoreContentType(false).header(
			"Content-Type","application/xhtml+xml").get();
			//System.out.println(doc);
			//doc.getElementsByAttribute(id)
			Elements data = doc.getAllElements();
			for (Element e: data) {
				System.out.println("ergh: " + e.toString());
			}
			return doc.getElementById(id).toString();
		} catch (IOException e) { e.printStackTrace(); return e.getMessage(); }
	} 
		
	public static String getPgn(String id) {
		GameData data = getGame(id); if (data == null) return null;
		String pgn = 
				"[White \"" + data.players.white.userId + "\"]" + cr +
				"[Black \"" + data.players.black.userId + "\"]" + cr + 
				"[Plycount \"" + data.turns + "\"]" + cr +
				cr;
		
		String[] moves = data.moves.split(" ");
		for (int i=0;i<moves.length;i++) {
			if (i % 2 == 0) pgn += ((i/2)+1) + "." + moves[i] + " ";
			else pgn += moves[i] + " ";
		}
		
		return pgn + "*";
	}
}
