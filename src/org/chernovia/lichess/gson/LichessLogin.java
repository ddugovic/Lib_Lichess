package org.chernovia.lichess.gson;

import java.util.List;

public class LichessLogin {
	String userName, title;
	boolean online, engine;
	String language;
	Profile profile;
	List<LoginGameData> nowPlaying;
	Perf bullet, chess960, classical, kingOfTheHill, puzzle, standard, threeCheck,
	antichess, atomic, horde, opening;
	String url;
	public List<LoginGameData> getNowPlaying() { return nowPlaying; }
}
