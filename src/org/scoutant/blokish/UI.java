/*
* Copyright (C) 2011- stephane coutant
*
* This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by
* the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
*
* This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; 
* without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. 
* See the GNU General Public License for more details.
*
* You should have received a copy of the GNU General Public License along with this program. If not, see <http://www.gnu.org/licenses/>
*/

package org.scoutant.blokish;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import org.scoutant.blokish.model.Move;
import org.scoutant.blokish.model.Piece;
import org.scoutant.blokish.model.Square;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.Window;

public class UI extends Activity {
	private static final int MENU_ITEM_HISTORY = 99;
	private static final int MENU_ITEM_REPLAY = 101;
	private static final int MENU_ITEM_BACK = 102;
	private static final int MENU_ITEM_NEW = 5;
	private static final int MENU_ITEM_THINK=10;
	private static final int MENU_ITEM_PREFERENCES=-1;
	private static final int MENU_ITEM_HELP = 9;
	private static final int MENU_ITEM_PASS_TURN = 12;
	private static final int MENU_ITEM_FLIP = 15;
	
	private static String tag = "activity";
	public GameView game;
	public boolean devmode=false;
	private SharedPreferences prefs;
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		newgame();
		prefs = PreferenceManager.getDefaultSharedPreferences(this);
	}

	private void newgame() {
		game = new GameView(UI.this);
		setContentView(game);
	}
	
	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		menu.clear();
		if (game.selected!=null) {
			menu.add(Menu.NONE, MENU_ITEM_FLIP, Menu.NONE, "Flip piece").setIcon(android.R.drawable.ic_menu_set_as);
		}
		menu.add(Menu.NONE, MENU_ITEM_BACK, Menu.NONE, "back one move").setIcon(android.R.drawable.ic_menu_revert);
		menu.add(Menu.NONE, MENU_ITEM_NEW, Menu.NONE, "new game").setIcon(android.R.drawable.ic_menu_rotate);
		menu.add(Menu.NONE, MENU_ITEM_PREFERENCES, Menu.NONE, "preferences").setIcon(android.R.drawable.ic_menu_preferences);
		menu.add(Menu.NONE, MENU_ITEM_HELP, Menu.NONE, "help").setIcon(android.R.drawable.ic_menu_help);
		if (devmode) {
			menu.add(Menu.NONE, MENU_ITEM_THINK, Menu.NONE, "AI").setIcon(android.R.drawable.ic_menu_manage);
			menu.add(Menu.NONE, MENU_ITEM_HISTORY, Menu.NONE, "hist").setIcon(android.R.drawable.ic_menu_recent_history);
		}
		if (!prefs.getBoolean("ai", true)) {
			menu.add(Menu.NONE, MENU_ITEM_PASS_TURN, Menu.NONE, "I pass my turn").setIcon(android.R.drawable.ic_menu_slideshow);			
		}
		return true;
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		super.onOptionsItemSelected(item);
		if (item.getItemId() == MENU_ITEM_HELP) {
			startActivity( new Intent(this, Help.class));			
		}
		if (item.getItemId() == MENU_ITEM_PREFERENCES) {
			startActivity( new Intent(this, Settings.class));
		}
		if (item.getItemId() == MENU_ITEM_HISTORY) {
			Log.d(tag, ""+game.game);
		}
		if (item.getItemId() == MENU_ITEM_REPLAY) {
			GameView old = game;
			newgame();
			Log.d(tag, "replay # moves : " + old.game.moves.size());
			game.replay( old.game.moves);
		}
		if (item.getItemId() == MENU_ITEM_BACK) {
			List<Move> moves = game.game.moves;
			int length = moves.size();
			if (length>=4) {
				length -= 4;
			}
			moves = moves.subList(0, length);
			newgame();
			Log.i(tag, "replay # moves : " + length);
			game.replay( moves);			
		}
		if (item.getItemId() == MENU_ITEM_NEW) {
			new AlertDialog.Builder(this)
			.setMessage("New Game ?")
			.setCancelable(false)
			.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int which) {
					newgame();
					}
				})
			.setNegativeButton("No", new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int id) {
						dialog.cancel();
					}
			})
			.create()
			.show();		
			}
		if (item.getItemId() == MENU_ITEM_THINK) {
			think(0);
		}
		if (item.getItemId() == MENU_ITEM_PASS_TURN) {
			turn = (turn+1)%4;
			game.showPieces(turn);
			game.invalidate();
		}
		if (item.getItemId() == MENU_ITEM_FLIP) {
			PieceUI piece = game.selected;
			if (piece!=null) piece.flip();
		}
		return false;
	}

	/**
	 * Invokes AI for all players from @param player. Thinking in a background Thread. But one player after the other! 
	 */
	public void think(int player) {
		turn = player;
		new AITask().execute(player);
	}
	
	
	private int findLevel() {
		String level = prefs.getString("aiLevel", "0");
		int l = new Integer(level);
		if (l<0 || l>3) l = 1;
		return Math.min(l, game.ai.adaptedLevel);
	}
	
	public int turn = 0;
	
	private class AITask extends AsyncTask<Integer, Void, Move> {
		@Override
		protected Move doInBackground(Integer... params) {
			game.thinking=true;
			return game.ai.think(params[0], findLevel());
		}
		@Override
		protected void onPostExecute(Move move) {
			if (game.game.over()) {
				displayWinnerDialog();
				return;
			}
			UI.this.game.play( move);
			turn++;
			if (turn<4) new AITask().execute(turn);
			if (turn==4 && !game.redOver ) {
				game.thinking=false;
				turn=0;
				game.showPieces(0);
				new CheckTask().execute();
			}
			if (turn==4 && game.redOver ) {
				Log.d(tag, "RED is DEAD. game.over ? " + game.game.over());
				if (game.game.over()) {
					displayWinnerDialog();					
				} else {
					turn = 1;
					new AITask().execute(turn);
				}
			}
		}
		private void displayWinnerDialog() {
			Log.d(tag, "game over !");
			int winner = game.game.winner();
			int score = game.game.boards.get(winner).score;
			String message = "";
			if (winner==0 && prefs.getBoolean("ai", true)) {
				 message += "Congratulations, you win with score : " + score +".";
				 if (findLevel()<4-1) message += "\nTry next level...";
			} else {
				message += "Player " + game.game.colors[winner] + " wins with score : " + score;
			}
			new AlertDialog.Builder(UI.this)
			.setMessage(message)
			.setCancelable(false)
			.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int which) {
					}
				})
			.create()
			.show();				
		}
	}
	
	private class CheckTask extends AsyncTask<Void, Void, Boolean> {
		@Override
		protected Boolean doInBackground(Void... params) {
			return !game.ai.hasMove(0);
		}
		@Override
		protected void onPostExecute(Boolean finished) {
			if (finished) {
				Log.d(tag, "red over!");
				new AlertDialog.Builder(UI.this)
				.setMessage("Red has no more moves...")
				.setCancelable(false)
				.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int which) {
						game.redOver = true;
						Log.d(tag, "ok!");
						think(1);
						}
					})
				.create()
				.show();						
			}
		}
	}
	
	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if ( keyCode == KeyEvent.KEYCODE_SEARCH) {
			devmode = !devmode;
			return true;
		}
		
		if ( keyCode == KeyEvent.KEYCODE_BACK) {
			if (!prefs.getBoolean("popupOnExit", true)) {
				UI.this.finish();
			} else {
			new AlertDialog.Builder(this)
			.setMessage("Quit game ?")
			.setCancelable(false)
			.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int which) {
					UI.this.finish();
					}
				})
			.setNegativeButton("No", new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int id) {
					}
				})
			.create()
			.show();
			return true;
			}
		}
		return super.onKeyDown(keyCode, event);
	}
	
	private void save(){
		FileOutputStream fos;
		try {
			fos = openFileOutput("moves.txt", Context.MODE_PRIVATE);
			if (!game.game.over()) {
				fos.write( game.game.toString().getBytes());
			} // if gave is over we do not save it, so as to open a blank game next time
			fos.close();
		} catch (FileNotFoundException e) {
			Log.e(tag, "not found...", e);
		} catch (IOException e) {
			Log.e(tag, "io...", e);
		}
	}
	/** sources a list of representions like this sample : 18|16|2|I3|0,-1|0,0|0,1 */
	private void source() {
		List<Move> list = new ArrayList<Move>();
		try {
			FileInputStream fis = openFileInput("moves.txt");
			BufferedReader reader = new BufferedReader( new InputStreamReader(fis));
			String line;
			reader.readLine(); // first line give the # of moves...
			while ((line = reader.readLine()) != null)   {
				String[] data = line.split(":");
				int i = new Integer(data[0]);
				int j = new Integer(data[1]);
				int color = new Integer(data[2]);
				Piece piece = game.game.boards.get(color).findPieceByType(data[3] );
				piece.reset();
				for (int q = 4; q<data.length; q++) {
					String[] position = data[q].split(",");
					int x = new Integer( position[0]);
					int y = new Integer( position[1]);
					piece.add( new Square(x, y ));
				}
				Move move = new Move(piece, i, j);
				Log.d(tag, "created move : " + move);
				list.add(move);
			}
			newgame();
			game.replay( list);
			game.reorderPieces();
		} catch (Exception e) {
			Log.e(tag, "yep error is :", e);
		}
	}

	@Override
	protected void onStart() {
		super.onStart();
		source();
	}
	@Override
	protected void onStop() {
		save();
		super.onStop();
	}
}