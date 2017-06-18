package org.chernovia.lichess;

import java.util.Iterator;
import java.util.List;

import org.chernovia.lichess.util.LichessUtils;

import com.codethesis.pgnparse.PGNGame;
import com.codethesis.pgnparse.PGNMove;
import com.codethesis.pgnparse.PGNParser;

public class ChessGame {
	public static String cr = "\n", pieceStr = "*pnbrqk";
	public static final int FILES = 8, RANKS = 8;
	public static int EMPTY = 0, PAWN = 1, KNIGHT = 2, BISHOP = 3, ROOK = 4, QUEEN = 5, KING = 6;
	String id;
	Square[][] board;
		
	public ChessGame(String gid) {
		id = gid;
		initFEN("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1");
	}
	
	public void update() {
		initFEN("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"); //TODO: get from lichess
		List<PGNGame> games = null;
		String pgn = LichessUtils.getPgn(id); if (pgn == null) return;
		//System.out.println("PGN: " + cr + pgn);
		try { games = PGNParser.parse(pgn); } 
		catch (Exception ergh) { ergh.printStackTrace(); System.exit(-1); }
		Iterator<PGNMove> moves = games.get(0).getMovesIterator();
		while (moves.hasNext()) {
			PGNMove move = moves.next();
			String from = move.getFromSquare(); String to = move.getToSquare(); 
			String piece = move.getPiece(); boolean cap = move.isCaptured();
			//System.out.println(from + " -> " + to);
			if (!move.isCastle() && !move.isEndGameMarked()) 
			makeMove(new ChessMove(
			from.charAt(0)-'a',
			RANKS - (from.charAt(1)-'0'),
			to.charAt(0)-'a',
			RANKS - (to.charAt(1)-'0'),
			piece,cap));
		}
		
	}
	
	//TODO: castling, en passant, promotions
	public void makeMove(ChessMove move) {
		//System.out.println(move.fromX + " -> " + to);
		int p = board[move.fromX][move.fromY].piece;
		board[move.fromX][move.fromY] = new Square(EMPTY);
		board[move.toX][move.toY] = new Square(p);
		//System.out.println(this);
	}
	
	public String toString() {
		String bs = ""; //= "Board: " + cr;
		for (int y=0;y<RANKS;y++) {
			for (int x=0;x<FILES;x++) {
				int p = board[x][y].piece;
				String piece = pieceStr.charAt(Math.abs(p)) + "";
				if (p > 0) piece = piece.toUpperCase();
				bs += piece;
			}
			bs += cr;
		}
		return bs;
	}
	
	public void initFEN(String FEN) {
		String[] fen = FEN.split(" ")[0].split("/");
		board = new Square[FILES][RANKS];
		for (int x=0;x<RANKS;x++) 
		for (int y=0;y<FILES;y++) {
			board[x][y] = new Square(EMPTY);
		}
		for (int rank=0;rank<fen.length;rank++)
		for (int i=0;i<fen[rank].length();i++) {
			switch(fen[rank].charAt(i)) {
				case 'p': board[i][rank] = new Square(-PAWN); break;
				case 'P': board[i][rank] = new Square(PAWN); break;
				case 'n': board[i][rank] = new Square(-KNIGHT); break;
				case 'N': board[i][rank] = new Square(KNIGHT); break;
				case 'b': board[i][rank] = new Square(-BISHOP); break;
				case 'B': board[i][rank] = new Square(BISHOP); break;
				case 'r': board[i][rank] = new Square(-ROOK); break;
				case 'R': board[i][rank] = new Square(ROOK); break;
				case 'q': board[i][rank] = new Square(-QUEEN); break;
				case 'Q': board[i][rank] = new Square(QUEEN); break;
				case 'k': board[i][rank] = new Square(-KING); break;
				case 'K': board[i][rank] = new Square(KING); break;
				default: i += Integer.parseInt(fen[rank].charAt(i) + "")-1;
			}
		}
	}
}

class ChessMove {
	int fromX, fromY, toX, toY;
	String Piece; boolean capture;
	public ChessMove(int x1, int y1, int x2, int y2, String P, boolean c) {
		fromX = x1; fromY = y1; toX = x2; toY = y2; Piece = P; capture = c;
	}
	public String toString() {
		return fromX + "," + fromY + "," + toX + "," + toY;
	}
}

class Square {
	int attacked;
	java.awt.Color color;
	int piece;
	
	public Square(int p) {
		attacked = 0; color = java.awt.Color.GRAY; piece = p;
	}
}



