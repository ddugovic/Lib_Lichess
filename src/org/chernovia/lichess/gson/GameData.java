package org.chernovia.lichess.gson;

import java.util.List;

public class GameData {
	public String id;
	public LichessPlayers players;
	public String winner;
	public boolean rated;
	public String variant, speed, perf;
	public long timestamp;
	public int turns;
	public String status;
	public String moves;
	public Opening opening;
	private List<String> fens;
	public List<String> getFens() { return fens; }
}
