package my.nt_crckr.battleships;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.ContextThemeWrapper;
import android.view.DragEvent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.dynamiclinks.DynamicLink;
import com.google.firebase.dynamiclinks.FirebaseDynamicLinks;
import com.google.firebase.dynamiclinks.PendingDynamicLinkData;
import com.google.firebase.dynamiclinks.ShortDynamicLink;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.installations.FirebaseInstallations;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    // Game Object for FireStore/games matching document style
    private static class Move {

        private int x;
        private int y;

        public Move() {
            // pass
        }

        public Move(int x, int y) {
            this.x = x;
            this.y = y;
        }

        public int getX() {
            return x;
        }

        public int getY() {
            return y;
        }
    }

    private static class Response {

        private boolean hit;

        public Response() {
            // pass
        }

        public Response(boolean hit) {
            this.hit = hit;
        }

        public boolean isHit() {
            return hit;
        }
    }

    private static class Player {

        private String id;
        private boolean left;

        public Player() {
            // pass
        }

        public Player(String id, boolean left) {
            this.id = id;
            this.left = left;
        }

        public String getId() {
            return id;
        }

        public boolean getLeft() {return left;}
    }

    private static class GameDocument {

        private int created;
        private boolean finished;
        private List<Move> moves;
        private List<Response> responses;
        private List<Ship> sunk;
        private Player player1;
        private Player player2;

        public GameDocument() {
            // pass
        }

        public GameDocument(int created, boolean finished, List<Move> moves,
                            List<Response> responses, List<Ship> sunk, Player player1, Player player2) {
            this.created = created;
            this.finished = finished;
            this.moves = moves;
            this.responses = responses;
            this.sunk = sunk;
            this.player1 = player1;
            this.player2 = player2;
        }

        public int getCreated() {
            return created;
        }

        public boolean isFinished() {
            return finished;
        }

        public List<Move> getMoves() {
            return moves;
        }

        public List<Response> getResponses() {
            return responses;
        }

        public List<Ship> getSunk() {
            return sunk;
        }

        public Player getPlayer1() {
            return player1;
        }

        public Player getPlayer2() {
            return player2;
        }
    }

    // Tag for Log.d()
    private static final String TAG = "Battleship";

    // Request codes for the UIs that we show with startActivityForResult:
    //final static int RC_SELECT_PLAYERS = 10000;
    //final static int RC_INVITATION_INBOX = 10001;
    //final static int RC_WAITING_ROOM = 10002;

    // Request code used to invoke sign in user interactions.
    //private static final int RC_SIGN_IN = 9001;

    // Firestore DateBase for games
    private final CollectionReference gamesDB = FirebaseFirestore.getInstance().collection("games");

    // Dynamic Links Object
    private final FirebaseDynamicLinks linkBuilder = FirebaseDynamicLinks.getInstance();

    // User id
    private final String user_id = FirebaseInstallations.getInstance().getId().toString();

    // Online game id
    private String game_id = null;

    // Online game DB reference
    private DocumentReference currentGameRef = null;

    // Online game object to view differences
    private GameDocument currentGame = null;

    // Client used to sign in with Google APIs
    //private GoogleSignInClient mGoogleSignInClient = null;

    // Client used to interact with the real time multiplayer system.
    //private RealTimeMultiplayerClient mRealTimeMultiplayerClient = null;

    // Client used to interact with the Invitation system.
    //private InvitationsClient mInvitationsClient = null;

    // Room ID where the currently active game is taking place;
    // null if we're not playing.
    String mRoomId = null;

    // Holds the configuration of the current room.
    //RoomConfig mRoomConfig;

    // Are we playing in multiplayer mode?
    boolean isMultiplayer = false;

    // The participants in the currently active game
    //ArrayList<Participant> mParticipants = null;

    // My participant ID in the currently active game
    //String mMyId = null;

    // If non-null, this is the id of the invitation we received via the
    // invitation listener
    String mIncomingInvitationId = null;

    // Difficulty of AI: EASY | -MEDIUM- | HARD
    private AIDifficulty singlePlayerDifficulty = AIDifficulty.HARD;

    // Gamemode type: CLASSIC | HITSTREAK
    private GameMode gameMode = GameMode.CLASSIC;


    // Цикл жизни

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        //Log.d(TAG, "onCreate()");
        super.onCreate(savedInstanceState);
        //setTheme(R.style.AppTheme_Dark);
        setContentView(R.layout.activity_main);
        hideSystemUI();

        switchToMainScreen();

        FirebaseDynamicLinks.getInstance()
                .getDynamicLink(getIntent())
                .addOnSuccessListener(this, new OnSuccessListener<PendingDynamicLinkData>() {
                    @Override
                    public void onSuccess(PendingDynamicLinkData pendingDynamicLinkData) {
                        // Get deep link from result (may be null if no link is found)
                        Uri deepLink = null;
                        if (pendingDynamicLinkData != null && !isMultiplayer) {
                            deepLink = pendingDynamicLinkData.getLink();
                            String link = null;
                            if (deepLink == null) {
                                Log.e(TAG, "Null deeplink");
                                return;
                            } else {
                                link = deepLink.toString();
                            }
                            Log.v(TAG, "Opened link: " + link);

                            game_id = link.substring(link.indexOf("game_id=") + 8);
                            isMultiplayer = true;
                            Log.v(TAG, "Game id: " + game_id);
                            DocumentReference docRef = gamesDB.document(game_id);

                            Player player2 = new Player(user_id, false);
                            docRef.update("player2", player2)
                                    .addOnSuccessListener(new OnSuccessListener<Void>() {
                                        @Override
                                        public void onSuccess(Void aVoid) {
                                            Log.d(TAG, "DocumentSnapshot successfully updated!");
                                        }
                                    })
                                    .addOnFailureListener(new OnFailureListener() {
                                        @Override
                                        public void onFailure(@NonNull Exception e) {
                                            Log.w(TAG, "Error updating document", e);
                                        }
                                    });

                            docRef.get().addOnCompleteListener(new OnCompleteListener<DocumentSnapshot>() {
                                @Override
                                public void onComplete(@NonNull Task<DocumentSnapshot> task) {
                                    if (task.isSuccessful()) {
                                        DocumentSnapshot doc = task.getResult();
                                        if (doc.exists()) {
                                            Log.d(TAG, "DocumentSnapshot data: " + doc.getData());
                                            currentGame = doc.toObject(GameDocument.class);
                                            startGameMultiPlayer();
                                        } else {
                                            Log.d(TAG, "No such document");
                                        }
                                    } else {
                                        Log.d(TAG, "get failed with ", task.getException());
                                    }
                                }
                            });

                            gamesDB.addSnapshotListener(new EventListener<QuerySnapshot>() {
                                @Override
                                public void onEvent(@Nullable QuerySnapshot snapshots,
                                                    @Nullable FirebaseFirestoreException e) {
                                    if (e != null) {
                                        Log.w(TAG, "listen:error", e);
                                        return;
                                    }

                                    if (snapshots != null)
                                        for (DocumentChange dc: snapshots.getDocumentChanges()) {
                                            if (dc.getDocument().getId().equals(game_id)) {
                                                switch (dc.getType()) {
                                                    case ADDED:
                                                        Log.d(TAG, "New game: " + dc.getDocument().getData());
                                                        break;
                                                    case MODIFIED:
                                                        Log.d(TAG, "Modified game: " + dc.getDocument().getData());
                                                        break;
                                                    case REMOVED:
                                                        Log.d(TAG, "Removed game: " + dc.getDocument().getData());
                                                        break;
                                                }
                                            }
                                        }
                                }
                            });
                        }
                    }
                })
                .addOnFailureListener(this, new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Log.w(TAG, "getDynamicLink:onFailure", e);
                    }
                });
    }

    @Override
    protected void onResume()
    {
        //Log.d(TAG, "onResume()");
        super.onResume();
    }

    @Override
    protected void onPause()
    {
        //Log.d(TAG, "onPause()");
        super.onPause();
    }


    // Fullscreen mode

    @Override
    public void onWindowFocusChanged(boolean hasFocus)
    {
        super.onWindowFocusChanged(hasFocus);

        if (hasFocus)
        {
            hideSystemUI();
        }
    }

    private void hideSystemUI()
    {
        View decorView = getWindow().getDecorView();
        int uiOptions = View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN;
        decorView.setSystemUiVisibility(uiOptions);
    }


    // User Interface

    // Current Screen id
    private int currentScreen = -1;

    // All Screens
    private static final int[] SCREENS = {
            R.id.screenMainOffline,
            R.id.screenPleaseWait,
            R.id.screenPlaceShips,
            R.id.screenTargetBoard,
            R.id.screenMyBoard };

    // All Popups
    private static final int[] POPUPS = {
            R.id.popupSetDifficulty,
            R.id.popupEndGame,
            R.id.popupQuitGame,
            R.id.popupInviteLink };

    private void switchToScreen(int screen)
    {
        currentScreen = screen;

        for (int s : SCREENS)
        {
            if (s == screen)
            {
                findViewById(s).setVisibility(View.VISIBLE);
            }
            else
            {
                findViewById(s).setVisibility(View.GONE);
            }
        }

        boolean showInvitePopup = false;

        if (mIncomingInvitationId == null)
        {
            showInvitePopup = false;
        }

        if (showInvitePopup)
        {
            findViewById(R.id.popupInvite).setVisibility(View.VISIBLE);
        }
        else
        {
            findViewById(R.id.popupInvite).setVisibility(View.GONE);
        }

        // Display Quit Button
        if (currentScreen == R.id.screenPlaceShips ||
                currentScreen == R.id.screenTargetBoard ||
                currentScreen == R.id.screenMyBoard)
        {
            findViewById(R.id.layoutQuitGame).setVisibility(View.VISIBLE);
        }
        else
        {
            findViewById(R.id.layoutQuitGame).setVisibility(View.GONE);
        }
    }

    private void switchToMainScreen()
    {
        switchToScreen(R.id.screenMainOffline);
    }

    private void showEndGamePopup(boolean win)
    {
        findViewById(R.id.popupEndGame).setVisibility(View.VISIBLE);

        if (isMultiplayer)
        {
            findViewById(R.id.button_play_again).setVisibility(View.GONE);

            if (win)
            {
                findViewById(R.id.image_trophy).setVisibility(View.VISIBLE);
                ((TextView) findViewById(R.id.text_winner)).setText(getString(R.string.you_win));
            }
            else
            {
                findViewById(R.id.image_trophy).setVisibility(View.GONE);
                //((TextView) findViewById(R.id.text_winner)).setText(getString(R.string.other_player_won, getOpponent().getDisplayName()));
            }
        }
        else
        {
            findViewById(R.id.button_play_again).setVisibility(View.VISIBLE);

            if (win)
            {
                findViewById(R.id.image_trophy).setVisibility(View.VISIBLE);
                ((TextView) findViewById(R.id.text_winner)).setText(getString(R.string.you_win));
            }
            else
            {
                findViewById(R.id.image_trophy).setVisibility(View.GONE);
                ((TextView) findViewById(R.id.text_winner)).setText(getString(R.string.you_lose));
            }
        }
    }

    private void displayPopup(int popup)
    {
        findViewById(popup).setVisibility(View.VISIBLE);
    }

    private void hidePopups()
    {
        for (int p : POPUPS)
        {
            findViewById(p).setVisibility(View.GONE);
        }
    }

    private void resetShipIcons()
    {
        ((ImageView)findViewById(R.id.image_my_carrier)).setImageResource(R.drawable.carrier);
        ((ImageView)findViewById(R.id.image_my_battleship)).setImageResource(R.drawable.battleship);
        ((ImageView)findViewById(R.id.image_my_cruiser)).setImageResource(R.drawable.cruiser);
        ((ImageView)findViewById(R.id.image_my_submarine)).setImageResource(R.drawable.submarine);
        ((ImageView)findViewById(R.id.image_my_destroyer)).setImageResource(R.drawable.destroyer);

        ((ImageView)findViewById(R.id.image_target_carrier)).setImageResource(R.drawable.carrier);
        ((ImageView)findViewById(R.id.image_target_battleship)).setImageResource(R.drawable.battleship);
        ((ImageView)findViewById(R.id.image_target_cruiser)).setImageResource(R.drawable.cruiser);
        ((ImageView)findViewById(R.id.image_target_submarine)).setImageResource(R.drawable.submarine);
        ((ImageView)findViewById(R.id.image_target_destroyer)).setImageResource(R.drawable.destroyer);
    }


    // Button Click Events

    public void buttonSinglePlayer(View view)
    {
        //Log.d(TAG, "SinglePlayer Button Clicked");
        displayPopup(R.id.popupSetDifficulty);
    }

    public void buttonEasy(View view)
    {
        //Log.d(TAG, "Easy Button Clicked");
        hidePopups();
        singlePlayerDifficulty = AIDifficulty.EASY;
        startGameSinglePlayer();
    }

    //public void buttonMedium(View view)
    //{
    //    //Log.d(TAG, "Medium Button Clicked");
    //    hidePopups();
    //    singlePlayerDifficulty = AIDifficulty.MEDIUM;
    //    startGameSinglePlayer();
    //}

    public void buttonHard(View view)
    {
        //Log.d(TAG, "Hard Button Clicked");
        hidePopups();
        singlePlayerDifficulty = AIDifficulty.HARD;
        startGameSinglePlayer();
    }

    public void buttonClassic(View view)
    {
        gameMode = GameMode.CLASSIC;
        ((TextView)findViewById(R.id.text_gamemode_description)).setText(getString(R.string.classic_description));
    }

    public void buttonHitStreak(View view)
    {
        gameMode = GameMode.HITSTREAK;
        ((TextView)findViewById(R.id.text_gamemode_description)).setText(getString(R.string.hitstreak_description));
    }

    public void buttonExitGame(View view)
    {
        //Log.d(TAG, "Exit Game Button Clicked");
        finishAndRemoveTask();

        android.os.Process.killProcess(android.os.Process.myPid());
        System.exit(0);
    }

    public void buttonInviteFriends(View view)
    {
        Log.d(TAG, "Invite Friends Button Clicked");
        switchToScreen(R.id.screenPleaseWait);

        GameDocument data = new GameDocument(
                getTime(),
                false,
                new ArrayList<Move>() {},
                new ArrayList<Response>() {},
                new ArrayList<Ship>(),
                new Player(user_id, false),
                new Player()
        );

        DocumentReference docRef = gamesDB.document();
        game_id = docRef.getId();
        docRef.set(data)
                .addOnSuccessListener(new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void aVoid) {
                        Log.d(TAG, "DocumentSnapshot written with ID: " + game_id);
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Log.w(TAG, "Error adding document", e);
                    }
                });

        linkBuilder.createDynamicLink()
                .setLink(Uri.parse("https://ntcrckrbattleships.page.link/?game_id=" + game_id))
                .setDomainUriPrefix("https://ntcrckrbattleships.page.link")
                .setAndroidParameters(
                        new DynamicLink.AndroidParameters.Builder()
                                .setMinimumVersion(1)
                                .build())
                .setSocialMetaTagParameters(
                        new DynamicLink.SocialMetaTagParameters.Builder()
                                .setTitle("Play Battleships with me!")
                                .setDescription("Click to join the match!")
                                .setImageUrl(Uri.parse("https://picsum.photos/500"))
                                .build())
                .buildShortDynamicLink()
                .addOnCompleteListener(this, new OnCompleteListener<ShortDynamicLink>() {
                    @Override
                    public void onComplete(@NonNull Task<ShortDynamicLink> task) {
                        if (task.isSuccessful()) {
                            // Short link created
                            ShortDynamicLink result = task.getResult();
                            Uri shortLink = result.getShortLink();
                            //Uri flowchartLink = result.getPreviewLink();

                            currentGameRef = docRef;
                            currentGame = data;

                            TextView tV_invite_link = findViewById(R.id.text_invite_link);
                            if (shortLink == null) {
                                Log.e(TAG, "Null short link");
                                return;
                            } else {
                                tV_invite_link.setText(shortLink.toString());
                            }
                            switchToMainScreen();
                            displayPopup(R.id.popupInviteLink);

                            gamesDB.addSnapshotListener(new EventListener<QuerySnapshot>() {
                                        @Override
                                        public void onEvent(@Nullable QuerySnapshot snapshots,
                                                            @Nullable FirebaseFirestoreException e) {
                                            if (e != null) {
                                                Log.w(TAG, "listen:error", e);
                                                return;
                                            }

                                            for (DocumentChange dc: snapshots.getDocumentChanges()) {
                                                if (dc.getDocument().getId().equals(game_id)) {
                                                    switch (dc.getType()) {
                                                        case ADDED:
                                                            Log.d(TAG, "New game: " + dc.getDocument().getData());
                                                            break;
                                                        case MODIFIED:
                                                            Log.d(TAG, "Modified game: " + dc.getDocument().getData());
                                                            startGameMultiPlayer();
                                                            break;
                                                        case REMOVED:
                                                            Log.d(TAG, "Removed game: " + dc.getDocument().getData());
                                                            break;
                                                    }
                                                }
                                            }

                                        }
                                    });
                        } else {
                            //Log.e(TAG, "Error occurred while getting short link");
                            for (ShortDynamicLink.Warning w: task.getResult().getWarnings()) {
                                Log.w(TAG, w.getMessage());
                            }
                            displayError("There was an error creating your invite link.");
                        }
                    }
                });
    }

    public void buttonInviteLinkCopy(View view)
    {
        TextView tV_invite_link = findViewById(R.id.text_invite_link);
        ClipboardManager clipboard = getSystemService(ClipboardManager.class);
        ClipData clip = ClipData.newPlainText(
                "invite link", tV_invite_link.getText()
        );
        clipboard.setPrimaryClip(clip);
        Toast.makeText(getApplicationContext(), "Invite link has been copied", Toast.LENGTH_LONG).show();
        hidePopups();
    }

    public void buttonRotateShip(View view)
    {
        //Log.d(TAG, "Rotate Ship Button Clicked");
        if (myDrawableBoardPlacing.getActiveShip() != null)
        {
            myDrawableBoardPlacing.getActiveShip().rotate();
            myDrawableBoardPlacing.colorShips();
        }
    }

    public void buttonPlaceShipsRandomly(View view)
    {
        //Log.d(TAG, "Place Ships Randomly Button Clicked");
        myBoard.placeShipsRandom();
        myDrawableBoardPlacing.setNoActiveShip();
        myDrawableBoardPlacing.colorShips();
    }

    public void buttonConfirmShips(View view)
    {
        //Log.d(TAG, "Confirm Ships Button Clicked");

        if (myBoard.isValidBoard())
        {
            myBoard.confirmShipLocations();
            switchToScreen(R.id.screenTargetBoard);
            displayDrawableBoards();
            ai = new AI(singlePlayerDifficulty, myBoard);
        }
        else
        {
            displayError(getString(R.string.invalid_ships_error));
        }
    }

    public void buttonPlayAgain(View view)
    {
        //Log.d(TAG, "Play Again Button Clicked");
        hidePopups();
        startGameSinglePlayer();
    }

    public void buttonReturnToMenu(View view)
    {
        //Log.d(TAG, "Return To Menu Button Clicked");
        hidePopups();

        if (isMultiplayer)
        {
            leaveRoom();
        }

        switchToMainScreen();
    }

    public void buttonResume(View view)
    {
        //Log.d(TAG, "Resume Button Clicked");
        hidePopups();
    }

    public void buttonQuitGame(View view)
    {
        //Log.d(TAG, "Quit Game Clicked");
        confirmQuit();
    }

    public void clickOutsidePopup(View view)
    {
        hidePopups();
    }

    public void clickInPopup(View view)
    {
        //
    }


    // Android UI / Hardware Back Button

    @Override
    public void onBackPressed()
    {
        if (currentScreen != R.id.screenMainOffline)
        {
            confirmQuit();
        }
    }


    //Alert Dialogs

    private void confirmQuit()
    {
        findViewById(R.id.popupQuitGame).setVisibility(View.VISIBLE);
    }

    private void displayError(String error)
    {
        final AlertDialog.Builder builder = new AlertDialog.Builder(new ContextThemeWrapper(this, R.style.AlertDialogCustom));
        builder.setTitle(R.string.error_title);
        builder.setMessage(error);

        builder.setNeutralButton(R.string.ok, null);

        builder.show();
    }


    // Handler and Runnables

    private final Handler handler = new Handler();

    private final Runnable timer = new Runnable()
    {
        @Override
        public void run()
        {
            if (timeRemaining < 0)
            {
                handler.removeCallbacks(this);
                return;
            }

            if (!myBoard.areShipsPlaced())
            {
                timerTick();
            }

            timeRemaining -= 1;
            handler.postDelayed(this, 1000);
        }
    };

    final Runnable aiTargetDelay = new Runnable()
    {
        @Override
        public void run()
        {
            Coordinate coordinate = ai.shoot();
            int x = coordinate.getX();
            int y = coordinate.getY();

            myDrawableBoard.colorCrosshair(x, y);

            if (myBoard.getStatus(x, y) == BoardStatus.HIDDEN_SHIP)
            {
                myBoard.setStatus(x, y, BoardStatus.HIT);
                myDrawableBoard.squares[x][y].setImage(R.drawable.hit);

                displaySunkShip(myBoard, myDrawableBoard);

                if (myBoard.allShipsSunk())
                {
                    handler.removeCallbacksAndMessages(this);
                    gameInProgress = false;
                    showEndGamePopup(false);
                }
                else
                {
                    if (gameMode == GameMode.CLASSIC)
                    {
                        handler.removeCallbacksAndMessages(this);
                        myTurn = true;
                        canTarget = true;
                        handler.postDelayed(delayTransition, 1000);
                    }
                    else if (gameMode == GameMode.HITSTREAK)
                    {
                        handler.postDelayed(this, 1000);
                    }
                }
            }
            else
            {
                handler.removeCallbacksAndMessages(this);
                myTurn = true;
                canTarget = true;
                myBoard.setStatus(x, y, BoardStatus.MISS);
                myDrawableBoard.squares[x][y].setImage(R.drawable.miss);
                handler.postDelayed(delayTransition, 1000);
            }
        }
    };

    final Runnable delayTransition = new Runnable()
    {
        @Override
        public void run()
        {
            if (myTurn)
            {
                switchToScreen(R.id.screenTargetBoard);
                myDrawableBoard.colorReset();
            }
            else
            {
                switchToScreen(R.id.screenMyBoard);
                targetDrawableBoard.colorReset();
            }
        }
    };


    // Game Logic

    // AI Object
    private AI ai;

    // Player's Board
    private Board myBoard;

    // AI's Board
    private Board aiBoard;

    // TODO
    private DrawableBoardPlacing myDrawableBoardPlacing;

    // Player's TODO
    private DrawableBoard myDrawableBoard;

    // Enemy's TODO
    private DrawableBoard targetDrawableBoard;

    // Is game in progress?
    private boolean gameInProgress = true;

    // Is it player's turn?
    private boolean myTurn = false;

    // Can player target? TODO
    private boolean canTarget = false;

    // How much time is remaining?
    private int timeRemaining = -1;

    // Time to place ships TODO
    private final int placeShipsTime = 20;

    // How many ships had sunk? TODO
    private int shipsSunk = 0;

    private void displayDrawableBoardPlacing()
    {
        DisplayMetrics displayMetrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
        int displayWidth = displayMetrics.widthPixels;
        int buttonSize = displayWidth / (BoardSize.COLUMNS + 1);

        myDrawableBoardPlacing = new DrawableBoardPlacing(this, myBoard, buttonSize);

        LinearLayout BattleshipGridPlacing = findViewById(R.id.battleship_grid_placing);
        BattleshipGridPlacing.removeAllViewsInLayout();
        BattleshipGridPlacing.addView(myDrawableBoardPlacing);
    }

    @SuppressLint("ClickableViewAccessibility")
    private void displayDrawableBoards()
    {
        DisplayMetrics displayMetrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
        int displayWidth = displayMetrics.widthPixels;
        int buttonSize = displayWidth / (BoardSize.COLUMNS + 1);

        // Targeting Board
        targetDrawableBoard = new DrawableBoard(this, buttonSize);

        for (int i = 0; i < BoardSize.ROWS; i++)
        {
            for (int j = 0; j < BoardSize.COLUMNS; j++)
            {
                final DrawableSquare square = targetDrawableBoard.squares[i][j];

                // Drag and Drop Event Handlers
                square.setOnTouchListener((view, motionEvent) -> {
                    if (gameInProgress && myTurn && canTarget)
                    {
                        if (motionEvent.getAction() == MotionEvent.ACTION_DOWN)
                        {
                            ClipData data = ClipData.newPlainText("", "");
                            View.DragShadowBuilder shadowBuilder = new MyDragShadowBuilder();
                            view.startDragAndDrop(data, shadowBuilder, view, 0);

                            return true;
                        }
                    }

                    return false;
                });

                square.setOnDragListener((view, dragEvent) -> {
                    switch(dragEvent.getAction())
                    {
                        case DragEvent.ACTION_DRAG_STARTED:
                            break;
                        case DragEvent.ACTION_DRAG_ENTERED:
                            DrawableSquare squareEnter = (DrawableSquare) view;
                            Coordinate squareEnterCoordinate = squareEnter.getCoordinate();
                            targetDrawableBoard.colorCrosshair(squareEnterCoordinate.getX(), squareEnterCoordinate.getY());
                            break;
                        case DragEvent.ACTION_DRAG_EXITED:
                            targetDrawableBoard.colorReset();
                            break;
                        case DragEvent.ACTION_DROP:
                            DrawableSquare square1 = (DrawableSquare) view;

                            if (!square1.isClicked())
                            {
                                targetCoordinate(square1);
                            }

                            break;
                        case DragEvent.ACTION_DRAG_ENDED:
                            break;
                        default:
                            break;
                    }

                    return true;
                });
            }
        }

        LinearLayout BattleshipGrid = findViewById(R.id.target_battleship_grid);
        BattleshipGrid.removeAllViewsInLayout();
        BattleshipGrid.addView(targetDrawableBoard);

        // My Board
        myDrawableBoard = new DrawableBoard(this, buttonSize);
        LinearLayout BattleshipGrid2 = findViewById(R.id.my_battleship_grid);
        BattleshipGrid2.removeAllViewsInLayout();
        BattleshipGrid2.addView(myDrawableBoard);
    }

    private void startGameSinglePlayer()
    {
        resetShipIcons();
        isMultiplayer = false;
        gameInProgress = true;
        myTurn = true;
        canTarget = true;
        switchToScreen(R.id.screenPlaceShips);
        findViewById(R.id.text_timer).setVisibility(View.GONE);
        ((TextView)findViewById(R.id.text_other_player_turn)).setText(getString(R.string.otherPlayerTurn, getString(R.string.ai)));
        findViewById(R.id.button_confirm_ships).setVisibility(View.VISIBLE);

        myBoard = new Board();
        displayDrawableBoardPlacing();

        aiBoard = new Board();
        aiBoard.placeShipsRandom();
        aiBoard.confirmShipLocations();
    }

    private void startGameMultiPlayer()
    {
        resetShipIcons();
        gameMode = GameMode.CLASSIC;
        isMultiplayer = true;
        gameInProgress = true;
        myTurn = false;
        canTarget = false;
        shipsSunk = 0;
        timeRemaining = placeShipsTime;
        switchToScreen(R.id.screenPlaceShips);
        findViewById(R.id.text_timer).setVisibility(View.VISIBLE);
        ((TextView)findViewById(R.id.text_other_player_turn)).setText("Your opponent");
        findViewById(R.id.button_confirm_ships).setVisibility(View.GONE);

        myBoard = new Board();
        displayDrawableBoardPlacing();

        handler.postDelayed(timer, 1000);

        determineStartingPlayer();
    }

    private void timerTick()
    {
        if (timeRemaining > 0)
        {
            ((TextView) findViewById(R.id.text_timer)).setText(String.valueOf(timeRemaining));
        }
        else if (timeRemaining == 0)
        {
            timeRemaining = -1;
            handler.removeCallbacks(timer);

            if (myBoard.isValidBoard())
            {
                myBoard.confirmShipLocations();
            }
            else
            {
                myBoard.placeShipsRandom();
                myBoard.confirmShipLocations();
            }

            myBoard.setShipsPlaced(true);

            handler.postDelayed(delayTransition, 0);

            displayDrawableBoards();
        }
    }

    private void targetCoordinate(DrawableSquare square)
    {
        canTarget = false;
        Coordinate coordinate = square.getCoordinate();
        int x = coordinate.getX();
        int y = coordinate.getY();

        targetDrawableBoard.colorCrosshair(x, y);

        if (isMultiplayer)
        {
            square.setClicked(true);
            //sendTargetCoordinate(x, y);
        }
        else
        {
            square.setClicked(true);

            if (aiBoard.getStatus(x, y) == BoardStatus.HIDDEN_SHIP)
            {
                if (gameMode == GameMode.HITSTREAK)
                {
                    canTarget = true;
                }

                square.setImage(R.drawable.hit);
                aiBoard.setStatus(x, y, BoardStatus.HIT);

                displaySunkShip(aiBoard, targetDrawableBoard);

                if (aiBoard.allShipsSunk())
                {
                    gameInProgress = false;
                    showEndGamePopup(true);
                }
                else
                {
                    if (gameMode == GameMode.CLASSIC)
                    {
                        myTurn = false;
                        handler.postDelayed(delayTransition, 600);
                        handler.postDelayed(aiTargetDelay, 1500);
                    }
                }
            }
            else
            {
                myTurn = false;
                square.setImage(R.drawable.miss);
                aiBoard.setStatus(x, y, BoardStatus.MISS);

                handler.postDelayed(delayTransition, 600);
                handler.postDelayed(aiTargetDelay, 1500);
            }
        }
    }

    private void displaySunkShip(Board board, DrawableBoard dBoard)
    {
        if (board.shipToSink() != null)
        {
            Ship ship = board.shipToSink();

            for (Coordinate c : ship.getListCoordinates())
            {
                dBoard.squares[c.getX()][c.getY()].setImage(R.drawable.sunk);
            }

            if (board == myBoard)
            {
                switch(ship.getType())
                {
                    case CARRIER:
                        ((ImageView)findViewById(R.id.image_my_carrier)).setImageResource(R.drawable.carrier_sunk);
                        break;
                    case BATTLESHIP:
                        ((ImageView)findViewById(R.id.image_my_battleship)).setImageResource(R.drawable.battleship_sunk);
                        break;
                    case CRUISER:
                        ((ImageView)findViewById(R.id.image_my_cruiser)).setImageResource(R.drawable.cruiser_sunk);
                        break;
                    case SUBMARINE:
                        ((ImageView)findViewById(R.id.image_my_submarine)).setImageResource(R.drawable.submarine_sunk);
                        break;
                    case DESTROYER:
                        ((ImageView)findViewById(R.id.image_my_destroyer)).setImageResource(R.drawable.destroyer_sunk);
                        break;
                }
            }
            else if (board == aiBoard)
            {
                switch(ship.getType())
                {
                    case CARRIER:
                        ((ImageView)findViewById(R.id.image_target_carrier)).setImageResource(R.drawable.carrier_sunk);
                        break;
                    case BATTLESHIP:
                        ((ImageView)findViewById(R.id.image_target_battleship)).setImageResource(R.drawable.battleship_sunk);
                        break;
                    case CRUISER:
                        ((ImageView)findViewById(R.id.image_target_cruiser)).setImageResource(R.drawable.cruiser_sunk);
                        break;
                    case SUBMARINE:
                        ((ImageView)findViewById(R.id.image_target_submarine)).setImageResource(R.drawable.submarine_sunk);
                        break;
                    case DESTROYER:
                        ((ImageView)findViewById(R.id.image_target_destroyer)).setImageResource(R.drawable.destroyer_sunk);
                        break;
                }
            }

            board.sinkShips();
        }
    }

    private void determineStartingPlayer()
    {
        if (user_id.equals(currentGame.getPlayer1().id)) {
            myTurn = true;
            canTarget = true;
        }
    }


    // Multiplayer Communications

    // Received intention to shoot
    private void receiveShot(Move move)
    {
        int x = move.getX();
        int y = move.getY();
        myDrawableBoard.colorCrosshair(x, y);
        // Hit
        if (myBoard.getStatus(x, y) == BoardStatus.HIDDEN_SHIP)
        {
            myBoard.setStatus(x, y, BoardStatus.HIT);

            // Sunk Ship
            if (myBoard.shipToSink() != null)
            {
                Ship ship = myBoard.shipToSink();
                displaySunkShip(myBoard, myDrawableBoard);
                sendSinkShip(ship);

                if (myBoard.allShipsSunk())
                {
                    gameInProgress = false;
                    showEndGamePopup(false);
                }
            }
            else
            {
                myDrawableBoard.squares[x][y].setImage(R.drawable.hit);
                sendSquareStatus(true);
            }

            if (gameMode == GameMode.CLASSIC)
            {
                myTurn = true;
                canTarget = true;
                handler.postDelayed(delayTransition, 1000);
            }
        }
        // Miss
        else
        {
            myBoard.setStatus(x, y, BoardStatus.MISS);
            myDrawableBoard.squares[x][y].setImage(R.drawable.miss);
            sendSquareStatus(false);
            myTurn = true;
            canTarget = true;
            handler.postDelayed(delayTransition, 1000);
        }
    }

    // Received Status of Targeted Location - Response to Target Location
    private void statusShot(Move point, boolean hit)
    {
        int x = point.getX();
        int y = point.getY();

        // If Hit, Set Square to Hit
        if (hit)
        {
            targetDrawableBoard.squares[x][y].setImage(R.drawable.hit);

            if (gameMode == GameMode.HITSTREAK)
            {
                canTarget = true;
            }
        }
        // If Miss - Set Square to Miss and End turn
        else
        {
            targetDrawableBoard.squares[x][y].setImage(R.drawable.miss);
            myTurn = false;
            handler.postDelayed(delayTransition, 1000);
        }
    }

    // Received Sink Ship
    private void recieveSunk(Ship ship)
    {
        int x = ship.getCenter().getX();
        int y = ship.getCenter().getY();
        ShipDirection direction = ship.getDirection();
        int length = ship.getLength();
        ShipType type = ship.getType();

        if (direction == ShipDirection.HORIZONTAL)
        {
            for (int i = x; i < x + length; i++)
            {
                targetDrawableBoard.squares[i][y].setImage(R.drawable.sunk);
            }
        }
        else if (direction == ShipDirection.VERTICAL)
        {
            for (int i = y; i < y + length; i++)
            {
                targetDrawableBoard.squares[x][i].setImage(R.drawable.sunk);
            }
        }

        switch(type)
        {
            case CARRIER:
                ((ImageView)findViewById(R.id.image_target_carrier)).setImageResource(R.drawable.carrier_sunk);
                break;
            case BATTLESHIP:
                ((ImageView)findViewById(R.id.image_target_battleship)).setImageResource(R.drawable.battleship_sunk);
                break;
            case CRUISER:
                ((ImageView)findViewById(R.id.image_target_cruiser)).setImageResource(R.drawable.cruiser_sunk);
                break;
            case SUBMARINE:
                ((ImageView)findViewById(R.id.image_target_submarine)).setImageResource(R.drawable.submarine_sunk);
                break;
            case DESTROYER:
                ((ImageView)findViewById(R.id.image_target_destroyer)).setImageResource(R.drawable.destroyer_sunk);
                break;
        }

        shipsSunk += 1;

        if (shipsSunk == 5)
        {
            gameInProgress = false;
            showEndGamePopup(true);
        }
        else
        {
            if (gameMode == GameMode.CLASSIC)
            {
                myTurn = false;
                handler.postDelayed(delayTransition, 1000);
            }
            else if (gameMode == GameMode.HITSTREAK)
            {
                canTarget = true;
            }
        }
    }

    private void sendSquareStatus(boolean hit)
    {
        currentGameRef.update("responses", FieldValue.arrayUnion(new Response(hit)));
    }

    private void sendSinkShip(Ship ship)
    {
        currentGameRef.update("sunk", FieldValue.arrayUnion(ship));
    }


    // Activity is going to the background. We have to leave the current room.
    @Override
    public void onStop()
    {
        //Log.d(TAG, "**** got onStop");

        // if we're in a room, leave it.
        leaveRoom();

        switchToMainScreen();

        super.onStop();
    }

    // Leave the room.
    void leaveRoom()
    {
        //Log.d(TAG, "Leaving room.");

        if (mRoomId != null)
        {
            currentGameRef.update("finished", true);

            switchToScreen(R.id.screenPleaseWait);
        }
        else
        {
            switchToMainScreen();
        }
    }

    // Show the waiting room UI to track the progress of other players as they enter the
    // room and get connected.
    /*void showWaitingRoom(Room room)
    {
        // minimum number of players required for our game
        // For simplicity, we require everyone to join the game before we start it
        // (this is signaled by Integer.MAX_VALUE).
        final int MIN_PLAYERS = Integer.MAX_VALUE;
        mRealTimeMultiplayerClient.getWaitingRoomIntent(room, MIN_PLAYERS).addOnSuccessListener(new OnSuccessListener<Intent>()
        {
            @Override
            public void onSuccess(Intent intent) {
                // show waiting room UI
                startActivityForResult(intent, RC_WAITING_ROOM);
            }
        }).addOnFailureListener(createFailureListener("There was a problem getting the waiting room!"));
    }

    private InvitationCallback mInvitationCallback = new InvitationCallback()
    {
        // Called when we get an invitation to play a game. We react by showing that to the user.
        @Override
        public void onInvitationReceived(@NonNull Invitation invitation)
        {
            // We got an invitation to play a game! So, store it in
            // mIncomingInvitationId
            // and show the popup on the screen.
            mIncomingInvitationId = invitation.getInvitationId();
            ((TextView) findViewById(R.id.text_incoming_invitation)).setText(getString(R.string.challenged_you, invitation.getInviter().getDisplayName()));
            switchToScreen(currentScreen); // This will show the invitation popup
        }

        @Override
        public void onInvitationRemoved(@NonNull String invitationId)
        {
            if (mIncomingInvitationId.equals(invitationId) && mIncomingInvitationId != null)
            {
                mIncomingInvitationId = null;
                switchToScreen(currentScreen); // This will hide the invitation popup
            }
        }
    };*/


    // Other
    private int getTime() {
        return (int) System.currentTimeMillis() / 1000;
    }
}