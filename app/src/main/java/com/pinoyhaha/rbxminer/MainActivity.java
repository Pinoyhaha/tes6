package com.pinoyhaha.rbxminer;

import android.app.Activity;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.Locale;
import java.util.Random;

public class MainActivity extends Activity {

    // IMPORTANT: replace this with your real deployed proofs site.
    // Example: https://your-app.vercel.app
    private static final String BACKEND_URL = "https://your-site.vercel.app";

    private static final int COINS_PER_ROBUX = 2000; // 20,000 coins = 10 Robux
    private static final int MIN_WITHDRAW = 80;
    private static final int NORMAL_DAILY_LIMIT = 5000;
    private static final int VIP_DAILY_LIMIT = 10000;
    private static final int CHEST_COST = 1500;

    private LinearLayout navBar;
    private LinearLayout contentRoot;
    private TextView topTitle;
    private TextView topCoins;
    private HorizontalScrollView navScroll;

    private String currentUser = "";
    private JSONObject userData = null;
    private JSONObject selectedRoblox = null;

    private Handler miningHandler = new Handler();
    private boolean mining = false;
    private String miningMode = "normal";
    private TextView consoleText;
    private TextView mineStatus;
    private TextView hashrateText;
    private TextView difficultyText;
    private Button mineButton;
    private Random random = new Random();

    interface JsonCallback {
        void done(JSONObject data);
        void fail(String error);
    }

    interface TextCallback {
        void done(String data);
        void fail(String error);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        navBar = findViewById(R.id.navBar);
        contentRoot = findViewById(R.id.contentRoot);
        topTitle = findViewById(R.id.topTitle);
        topCoins = findViewById(R.id.topCoins);
        navScroll = findViewById(R.id.navScroll);

        showAuthNav(false);
        showLogin();
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    private String cleanName(String s) {
        if (s == null) return "";
        return s.trim().toLowerCase(Locale.US).replace(".", "_").replace("#", "_").replace("$", "_").replace("[", "_").replace("]", "_").replace("/", "_");
    }

    private String fmt(long n) {
        return String.format(Locale.US, "%,d", n);
    }

    private long num(JSONObject obj, String key) {
        if (obj == null) return 0;
        return obj.optLong(key, obj.optLong("balance", 0));
    }

    private boolean isOwner() {
        if (userData == null) return false;
        return "owner".equalsIgnoreCase(userData.optString("role", "miner"));
    }

    private boolean isVip() {
        if (userData == null) return false;
        if (!userData.optBoolean("vip", false)) return false;
        long until = userData.optLong("vipUntil", 0);
        return until == 0 || until > System.currentTimeMillis() / 1000;
    }

    private int dailyLimit() {
        return isVip() ? VIP_DAILY_LIMIT : NORMAL_DAILY_LIMIT;
    }

    private String todayKey() {
        return new SimpleDateFormat("yyyyMMdd", Locale.US).format(new Date());
    }

    private String dateKey(long seconds) {
        if (seconds <= 0) return "";
        return new SimpleDateFormat("yyyyMMdd", Locale.US).format(new Date(seconds * 1000));
    }

    private void clear() {
        contentRoot.removeAllViews();
    }

    private TextView text(String value, int sp, int color, int style) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setTypeface(Typeface.DEFAULT, style);
        t.setPadding(0, dp(4), 0, dp(4));
        return t;
    }

    private EditText input(String hint) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setHintTextColor(Color.argb(130, 248, 255, 251));
        e.setTextColor(Color.WHITE);
        e.setSingleLine(true);
        e.setPadding(dp(14), 0, dp(14), 0);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(52));
        lp.setMargins(0, dp(6), 0, dp(6));
        e.setLayoutParams(lp);
        e.setBackground(round(Color.argb(28, 255, 255, 255), dp(18), Color.argb(55, 255, 255, 255)));
        return e;
    }

    private Button btn(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextColor(Color.rgb(7, 20, 14));
        b.setTextSize(14);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setAllCaps(false);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(50));
        lp.setMargins(0, dp(6), 0, dp(6));
        b.setLayoutParams(lp);
        GradientDrawable gd = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{Color.rgb(255, 230, 109), Color.rgb(101, 255, 178)});
        gd.setCornerRadius(dp(18));
        b.setBackground(gd);
        return b;
    }

    private Button smallBtn(String label) {
        Button b = btn(label);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(116), dp(44));
        lp.setMargins(dp(4), dp(4), dp(4), dp(4));
        b.setLayoutParams(lp);
        return b;
    }

    private GradientDrawable round(int color, int radius, int strokeColor) {
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(color);
        gd.setCornerRadius(radius);
        gd.setStroke(dp(1), strokeColor);
        return gd;
    }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(16), dp(16), dp(16), dp(16));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, dp(8), 0, dp(8));
        c.setLayoutParams(lp);
        c.setBackground(round(Color.argb(38, 255, 255, 255), dp(26), Color.argb(52, 255, 255, 255)));
        return c;
    }

    private LinearLayout row() {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(0, dp(4), 0, dp(4));
        return r;
    }

    private void showAuthNav(boolean show) {
        navScroll.setVisibility(show ? View.VISIBLE : View.GONE);
        topCoins.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    private void buildNav() {
        navBar.removeAllViews();
        addNav("Home", new View.OnClickListener() { public void onClick(View v) { showHome(); } });
        addNav("Mine", new View.OnClickListener() { public void onClick(View v) { showMine(); } });
        addNav("Withdraw", new View.OnClickListener() { public void onClick(View v) { showWithdraw(); } });
        addNav("Daily", new View.OnClickListener() { public void onClick(View v) { showDaily(); } });
        addNav("Chest", new View.OnClickListener() { public void onClick(View v) { showChest(); } });
        addNav("Results", new View.OnClickListener() { public void onClick(View v) { showResults(); } });
        addNav("Profile", new View.OnClickListener() { public void onClick(View v) { showProfile(); } });
        if (isOwner()) addNav("Owner", new View.OnClickListener() { public void onClick(View v) { showOwner(); } });
        addNav("Logout", new View.OnClickListener() { public void onClick(View v) { logout(); } });
    }

    private void addNav(String label, View.OnClickListener listener) {
        Button b = smallBtn(label);
        b.setOnClickListener(listener);
        navBar.addView(b);
    }

    private void updateTop() {
        if (currentUser.length() == 0 || userData == null) {
            topTitle.setText("RBX Miner");
            topCoins.setText("0 coins");
            return;
        }
        topTitle.setText(currentUser);
        topCoins.setText(fmt(num(userData, "coins")) + " coins");
    }

    private String enc(String s) throws Exception {
        return URLEncoder.encode(s, "UTF-8");
    }

    private String qx(String path) throws Exception {
        return BACKEND_URL.replaceAll("/+$", "") + "/.netlify/functions/qx?p=" + enc(path);
    }

    private String rb(String username) throws Exception {
        return BACKEND_URL.replaceAll("/+$", "") + "/.netlify/functions/rb?u=" + enc(username);
    }

    private void http(final String method, final String urlText, final String body, final TextCallback cb) {
        new AsyncTask<Void, Void, String>() {
            String err = "";
            protected String doInBackground(Void... params) {
                try {
                    URL url = new URL(urlText);
                    HttpURLConnection con = (HttpURLConnection) url.openConnection();
                    con.setRequestMethod(method);
                    con.setConnectTimeout(12000);
                    con.setReadTimeout(12000);
                    con.setRequestProperty("Content-Type", "application/json");
                    if (body != null) {
                        con.setDoOutput(true);
                        OutputStream os = con.getOutputStream();
                        os.write(body.getBytes("UTF-8"));
                        os.close();
                    }
                    int code = con.getResponseCode();
                    BufferedReader br = new BufferedReader(new InputStreamReader(code >= 400 ? con.getErrorStream() : con.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) sb.append(line);
                    br.close();
                    if (code >= 400) throw new Exception("HTTP " + code + " " + sb.toString());
                    return sb.toString();
                } catch (Exception e) {
                    err = e.getMessage();
                    return null;
                }
            }
            protected void onPostExecute(String s) {
                if (s == null) cb.fail(err == null ? "Network failed" : err);
                else cb.done(s);
            }
        }.execute();
    }

    private void get(final String path, final JsonCallback cb) {
        try {
            http("GET", qx(path), null, new TextCallback() {
                public void done(String data) {
                    try {
                        if (data == null || data.equals("null") || data.length() == 0) cb.done(null);
                        else cb.done(new JSONObject(data));
                    } catch (Exception e) { cb.fail(e.getMessage()); }
                }
                public void fail(String error) { cb.fail(error); }
            });
        } catch (Exception e) { cb.fail(e.getMessage()); }
    }

    private void put(final String path, final Object value, final TextCallback cb) {
        try {
            String body;
            if (value instanceof JSONObject) body = value.toString();
            else if (value instanceof String) body = JSONObject.quote(String.valueOf(value));
            else body = String.valueOf(value);
            http("PUT", qx(path), body, cb);
        } catch (Exception e) { cb.fail(e.getMessage()); }
    }

    private void post(final String path, final JSONObject value, final TextCallback cb) {
        try {
            http("POST", qx(path), value.toString(), cb);
        } catch (Exception e) { cb.fail(e.getMessage()); }
    }

    private void checkRoblox(final String username, final JsonCallback cb) {
        try {
            http("GET", rb(username), null, new TextCallback() {
                public void done(String data) {
                    try { cb.done(new JSONObject(data)); }
                    catch (Exception e) { cb.fail("Roblox check failed"); }
                }
                public void fail(String error) { cb.fail("Roblox user not found"); }
            });
        } catch (Exception e) { cb.fail(e.getMessage()); }
    }

    private void showLogin() {
        stopMining();
        showAuthNav(false);
        clear();
        topTitle.setText("RBX Miner");

        LinearLayout c = card();
        c.addView(text("Welcome back", 12, Color.rgb(101, 255, 178), Typeface.BOLD));
        c.addView(text("Login", 34, Color.WHITE, Typeface.BOLD));
        c.addView(text("Use your app account only.", 14, Color.argb(180, 248, 255, 251), Typeface.NORMAL));
        final EditText user = input("App username");
        final EditText pass = input("App password");
        pass.setInputType(0x00000081);
        Button login = btn("Login");
        Button reg = btn("Create account");
        c.addView(user); c.addView(pass); c.addView(login); c.addView(reg);
        contentRoot.addView(c);

        login.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                final String u = cleanName(user.getText().toString());
                final String p = pass.getText().toString();
                if (u.length() == 0 || p.length() == 0) { toast("Fill login"); return; }
                toast("Logging in...");
                get("users/" + u, new JsonCallback() {
                    public void done(JSONObject data) {
                        if (data == null) { toast("User not found"); return; }
                        if (!p.equals(data.optString("password", ""))) { toast("Wrong password"); return; }
                        if (data.optBoolean("banned", false)) { toast("Account banned"); return; }
                        currentUser = u;
                        userData = data;
                        miningMode = userData.optString("miningMode", "normal");
                        showAuthNav(true);
                        buildNav();
                        updateTop();
                        showHome();
                        toast("Login success");
                    }
                    public void fail(String error) { toast("Login failed"); }
                });
            }
        });
        reg.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { showRegister(); } });
    }

    private void showRegister() {
        showAuthNav(false);
        clear();
        LinearLayout c = card();
        c.addView(text("New miner", 12, Color.rgb(101, 255, 178), Typeface.BOLD));
        c.addView(text("Register", 34, Color.WHITE, Typeface.BOLD));
        c.addView(text("Never enter your Roblox password. Only your Roblox username.", 14, Color.argb(180, 248, 255, 251), Typeface.NORMAL));
        final EditText user = input("App username");
        final EditText pass = input("App password");
        pass.setInputType(0x00000081);
        final EditText roblox = input("Roblox username or ID");
        Button create = btn("Register");
        Button back = btn("Back to login");
        c.addView(user); c.addView(pass); c.addView(roblox); c.addView(create); c.addView(back);
        contentRoot.addView(c);

        create.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                final String u = cleanName(user.getText().toString());
                final String p = pass.getText().toString();
                final String r = roblox.getText().toString().trim();
                if (u.length() == 0 || p.length() == 0 || r.length() == 0) { toast("Fill all fields"); return; }
                get("users/" + u, new JsonCallback() {
                    public void done(JSONObject existing) {
                        if (existing != null) { toast("Username already exists"); return; }
                        toast("Checking Roblox...");
                        checkRoblox(r, new JsonCallback() {
                            public void done(JSONObject rbx) {
                                try {
                                    JSONObject data = new JSONObject();
                                    data.put("password", p);
                                    data.put("role", "miner");
                                    data.put("coins", 0);
                                    data.put("totalCoinsMined", 0);
                                    data.put("blocksFound", 0);
                                    data.put("acceptedShares", 0);
                                    data.put("rejectedShares", 0);
                                    data.put("highestMineReward", 0);
                                    data.put("miningMode", "normal");
                                    data.put("robloxUsername", rbx.optString("name", r));
                                    data.put("robloxDisplayName", rbx.optString("displayName", rbx.optString("name", r)));
                                    data.put("robloxId", rbx.optString("id", ""));
                                    data.put("robloxAvatarUrl", rbx.optString("avatarUrl", ""));
                                    data.put("vip", false);
                                    data.put("vipUntil", 0);
                                    data.put("banned", false);
                                    data.put("createdAt", System.currentTimeMillis()/1000);
                                    put("users/" + u, data, new TextCallback() {
                                        public void done(String s) { toast("Registered"); showLogin(); }
                                        public void fail(String error) { toast("Register save failed"); }
                                    });
                                } catch (Exception e) { toast("Register failed"); }
                            }
                            public void fail(String error) { toast(error); }
                        });
                    }
                    public void fail(String error) { toast("Register failed"); }
                });
            }
        });
        back.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { showLogin(); } });
    }

    private void showHome() {
        showAuthNav(true);
        buildNav();
        updateTop();
        clear();
        LinearLayout hero = card();
        hero.addView(text(isVip() ? "VIP MINER" : "FREE MINER", 12, Color.rgb(101,255,178), Typeface.BOLD));
        hero.addView(text(currentUser, 28, Color.WHITE, Typeface.BOLD));
        hero.addView(text("@" + userData.optString("robloxUsername", "roblox"), 14, Color.argb(180,248,255,251), Typeface.NORMAL));
        hero.addView(text(fmt(num(userData, "coins")) + " coins", 26, Color.rgb(255,230,109), Typeface.BOLD));
        contentRoot.addView(hero);

        LinearLayout quick = card();
        quick.addView(text("Start earning", 12, Color.rgb(101,255,178), Typeface.BOLD));
        quick.addView(text("Mine Coins Fast", 26, Color.WHITE, Typeface.BOLD));
        quick.addView(text("Native AIDE version of your proofs app.", 14, Color.argb(180,248,255,251), Typeface.NORMAL));
        Button mine = btn("Start Mining"); quick.addView(mine); contentRoot.addView(quick);
        mine.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){ showMine(); }});

        addRecentRedeems();
    }

    private void addStat(LinearLayout parent, String title, String value) {
        LinearLayout c = card();
        c.addView(text(title, 12, Color.argb(170,248,255,251), Typeface.BOLD));
        c.addView(text(value, 20, Color.rgb(255,230,109), Typeface.BOLD));
        parent.addView(c);
    }

    private void showMine() {
        updateTop(); clear();
        LinearLayout c = card();
        c.addView(text("Mining Dashboard", 28, Color.WHITE, Typeface.BOLD));
        Button normal = btn("Normal Mode");
        Button hard = btn("Hard Mode");
        c.addView(normal); c.addView(hard);
        mineStatus = text("Status: READY", 16, Color.WHITE, Typeface.BOLD);
        hashrateText = text("Hashrate: 0 MH/s", 15, Color.argb(200,248,255,251), Typeface.NORMAL);
        difficultyText = text("Difficulty: 0", 15, Color.argb(200,248,255,251), Typeface.NORMAL);
        mineButton = btn("Start Mining");
        consoleText = text("> waiting for miner...", 13, Color.rgb(170,255,214), Typeface.NORMAL);
        c.addView(mineStatus); c.addView(hashrateText); c.addView(difficultyText); c.addView(mineButton); c.addView(consoleText);
        contentRoot.addView(c);
        normal.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){ miningMode="normal"; toast("Normal mode"); }});
        hard.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){ miningMode="hard"; toast("Hard mode"); }});
        mineButton.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){ toggleMining(); }});
    }

    private void log(String s) {
        if (consoleText != null) consoleText.setText("> " + s + "\n" + consoleText.getText().toString());
    }

    private void toggleMining() {
        if (mining) { stopMining(); saveUser(null); return; }
        mining = true;
        mineButton.setText("Stop Mining");
        mineStatus.setText("Status: MINING");
        log("connected as " + currentUser + ".worker");
        miningHandler.postDelayed(mineTick, 1000);
    }

    private Runnable mineTick = new Runnable() {
        public void run() {
            if (!mining || userData == null) return;
            boolean hard = "hard".equals(miningMode);
            int roll = random.nextInt(100) + 1;
            int reward = 0;
            double boost = isVip() ? 2.0 : 1.0;
            double hash = 8 + random.nextDouble() * 30;
            int diff = hard ? 25000 + random.nextInt(35000) : 5000 + random.nextInt(9000);
            if (hard) {
                if (roll <= 3) { reward = (int)((3000 + random.nextInt(6000)) * boost); addLong("blocksFound", 1); addLong("acceptedShares", 1); log("[HARD] rare block +" + fmt(reward) + " coins"); }
                else if (roll <= 38) { reward = (int)((450 + random.nextInt(750)) * boost); addLong("acceptedShares", 1); log("[HARD] accepted share +" + fmt(reward)); }
                else { addLong("rejectedShares", 1); log("[HARD] nonce rejected"); }
            } else {
                if (roll <= 7) { reward = (int)((600 + random.nextInt(800)) * boost); addLong("blocksFound", 1); addLong("acceptedShares", 1); log("[NORMAL] lucky block +" + fmt(reward) + " coins"); }
                else if (roll <= 75) { reward = (int)((80 + random.nextInt(170)) * boost); addLong("acceptedShares", 1); log("[NORMAL] accepted share +" + fmt(reward)); }
                else { addLong("rejectedShares", 1); log("[NORMAL] nonce rejected"); }
            }
            if (reward > 0) { addLong("coins", reward); addLong("totalCoinsMined", reward); }
            hashrateText.setText("Hashrate: " + String.format(Locale.US, "%.2f", hash) + " MH/s");
            difficultyText.setText("Difficulty: " + fmt(diff));
            updateTop();
            saveUser(null);
            miningHandler.postDelayed(this, 1500);
        }
    };

    private void stopMining() {
        mining = false;
        miningHandler.removeCallbacks(mineTick);
        if (mineButton != null) mineButton.setText("Start Mining");
        if (mineStatus != null) mineStatus.setText("Status: READY");
    }

    private void addLong(String key, long add) {
        try { userData.put(key, userData.optLong(key, 0) + add); } catch(Exception e) {}
    }

    private void saveUser(final TextCallback cb) {
        if (currentUser.length() == 0 || userData == null) return;
        try {
            JSONObject data = new JSONObject(userData.toString());
            data.put("miningMode", miningMode);
            put("users/" + currentUser, data, cb == null ? new TextCallback(){ public void done(String s){} public void fail(String e){} } : cb);
        } catch(Exception e) {}
    }

    private void showDaily() {
        clear(); updateTop();
        LinearLayout c = card();
        c.addView(text("Daily Spin", 28, Color.WHITE, Typeface.BOLD));
        c.addView(text("Claim once per day. Reward: 50-1,000 coins.", 14, Color.argb(180,248,255,251), Typeface.NORMAL));
        final TextView result = text("Tap claim to spin", 20, Color.rgb(255,230,109), Typeface.BOLD);
        Button claim = btn("Claim Daily Spin");
        c.addView(result); c.addView(claim); contentRoot.addView(c);
        claim.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){
            if (todayKey().equals(userData.optString("lastDailySpinDate", ""))) { toast("Daily already claimed"); return; }
            int reward = 50 + random.nextInt(951);
            addLong("coins", reward); addLong("totalCoinsMined", reward);
            try { userData.put("lastDailySpinDate", todayKey()); userData.put("lastDailySpinReward", reward); } catch(Exception e) {}
            result.setText("You won " + fmt(reward) + " coins!");
            updateTop(); saveUser(null); toast("+" + fmt(reward) + " coins");
        }});
    }

    private void showChest() {
        clear(); updateTop();
        LinearLayout c = card();
        c.addView(text("Lucky Chest", 28, Color.WHITE, Typeface.BOLD));
        c.addView(text("Cost: 1,500 coins. Odds: 1% = 5,000,000, 30% = 2,000, 69% = 0.", 14, Color.argb(180,248,255,251), Typeface.NORMAL));
        final TextView result = text("Tap buy to open", 20, Color.rgb(255,230,109), Typeface.BOLD);
        Button buy = btn("Buy Chest");
        c.addView(result); c.addView(buy); contentRoot.addView(c);
        buy.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){
            if (num(userData, "coins") < CHEST_COST) { toast("Not enough coins"); return; }
            addLong("coins", -CHEST_COST);
            int roll = random.nextInt(100) + 1;
            int reward = roll <= 1 ? 5000000 : (roll <= 31 ? 2000 : 0);
            if (reward > 0) addLong("coins", reward);
            result.setText(reward > 0 ? "You won " + fmt(reward) + " coins!" : "No win this time");
            updateTop(); saveUser(null);
        }});
    }

    private void showWithdraw() {
        clear(); updateTop();
        addRedeemPackages();
        LinearLayout c = card();
        c.addView(text("Withdraw Robux", 28, Color.WHITE, Typeface.BOLD));
        c.addView(text("Balance: " + fmt(num(userData, "coins")) + " coins • Daily limit: " + fmt(dailyLimit()) + " Robux", 13, Color.argb(180,248,255,251), Typeface.NORMAL));
        final EditText rbx = input("Roblox username or ID");
        rbx.setText(userData.optString("robloxUsername", ""));
        Button check = btn("Check Account");
        final TextView status = text("Check the Roblox account first.", 13, Color.argb(180,248,255,251), Typeface.NORMAL);
        final EditText amount = input("Robux amount");
        final EditText extra = input("Promo / extra info optional");
        Button submit = btn("Submit Withdraw");
        c.addView(rbx); c.addView(check); c.addView(status); c.addView(amount); c.addView(extra); c.addView(submit);
        contentRoot.addView(c);
        addRecentRedeems();

        check.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){
            String name = rbx.getText().toString().trim();
            if (name.length() < 3) { toast("Enter Roblox username"); return; }
            status.setText("Checking...");
            checkRoblox(name, new JsonCallback(){
                public void done(JSONObject data){ selectedRoblox = data; status.setText("Found: " + data.optString("displayName") + " (@" + data.optString("name") + ")"); }
                public void fail(String error){ selectedRoblox = null; status.setText(error); }
            });
        }});
        submit.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){
            submitWithdraw(amount.getText().toString(), extra.getText().toString());
        }});
    }

    private void addRedeemPackages() {
        LinearLayout c = card();
        c.addView(text("Redeem Packages", 12, Color.rgb(101,255,178), Typeface.BOLD));
        LinearLayout row = new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL);
        int[] packs = new int[]{80,100,250,500,1000,5000};
        for (int i=0;i<packs.length;i++) {
            TextView pill = text(packs[i] + " R$\n" + fmt((long)packs[i]*COINS_PER_ROBUX) + " coins", 11, Color.WHITE, Typeface.BOLD);
            pill.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(100), dp(48)); lp.setMargins(dp(4), dp(4), dp(4), dp(4));
            pill.setLayoutParams(lp);
            pill.setBackground(round(Color.argb(42,101,255,178), dp(16), Color.argb(55,101,255,178)));
            row.addView(pill);
        }
        HorizontalScrollView hsv = new HorizontalScrollView(this); hsv.setHorizontalScrollBarEnabled(false); hsv.addView(row); c.addView(hsv); contentRoot.addView(c);
    }

    private void submitWithdraw(String amountText, String extraInfo) {
        if (selectedRoblox == null) { toast("Check Roblox account first"); return; }
        int robux;
        try { robux = Integer.parseInt(amountText.trim()); } catch(Exception e) { toast("Enter amount"); return; }
        if (robux < MIN_WITHDRAW) { toast("Minimum is " + MIN_WITHDRAW + " Robux"); return; }
        if (robux > dailyLimit()) { toast("Daily limit is " + dailyLimit() + " Robux"); return; }
        long cost = (long) robux * COINS_PER_ROBUX;
        if (num(userData, "coins") < cost) { toast("Not enough coins"); return; }
        try {
            JSONObject req = new JSONObject();
            req.put("username", currentUser);
            req.put("vip", isVip());
            req.put("robloxUsername", selectedRoblox.optString("name"));
            req.put("robloxDisplayName", selectedRoblox.optString("displayName"));
            req.put("robloxId", selectedRoblox.optString("id"));
            req.put("robuxAmount", robux);
            req.put("coinsCost", cost);
            req.put("extraInfo", extraInfo);
            req.put("promoInfo", extraInfo);
            req.put("note", extraInfo);
            req.put("status", "pending");
            req.put("createdAt", System.currentTimeMillis()/1000);
            toast("Submitting...");
            post("withdrawals", req, new TextCallback(){
                public void done(String s){ toast("Withdraw request sent"); showResults(); }
                public void fail(String error){ toast("Submit failed"); }
            });
        } catch(Exception e) { toast("Submit failed"); }
    }

    private void showResults() {
        clear(); updateTop();
        final LinearLayout c = card();
        c.addView(text("My Results", 28, Color.WHITE, Typeface.BOLD));
        c.addView(text("Loading...", 14, Color.argb(180,248,255,251), Typeface.NORMAL));
        contentRoot.addView(c);
        get("withdrawals", new JsonCallback(){
            public void done(JSONObject data){
                c.removeAllViews(); c.addView(text("My Results", 28, Color.WHITE, Typeface.BOLD));
                if (data == null) { c.addView(text("No results yet.", 14, Color.WHITE, Typeface.NORMAL)); return; }
                Iterator<String> keys = data.keys(); boolean any = false;
                while(keys.hasNext()) {
                    String id = keys.next(); JSONObject x = data.optJSONObject(id);
                    if (x == null || !currentUser.equals(x.optString("username"))) continue;
                    any = true;
                    c.addView(text(x.optString("status", "pending") + " • " + x.optInt("robuxAmount") + " Robux\nID: " + id, 14, Color.rgb(255,230,109), Typeface.BOLD));
                }
                if (!any) c.addView(text("No results yet.", 14, Color.WHITE, Typeface.NORMAL));
            }
            public void fail(String error){ c.addView(text("Failed to load results.", 14, Color.WHITE, Typeface.NORMAL)); }
        });
    }

    private void addRecentRedeems() {
        final LinearLayout c = card();
        c.addView(text("Recently Redeemed", 18, Color.WHITE, Typeface.BOLD));
        c.addView(text("Loading...", 12, Color.argb(180,248,255,251), Typeface.NORMAL));
        contentRoot.addView(c);
        get("withdrawals", new JsonCallback(){
            public void done(JSONObject data){
                c.removeAllViews(); c.addView(text("Recently Redeemed", 18, Color.WHITE, Typeface.BOLD));
                if (data == null) { c.addView(text("No verified redeems yet.", 12, Color.WHITE, Typeface.NORMAL)); return; }
                LinearLayout row = new LinearLayout(MainActivity.this); row.setOrientation(LinearLayout.HORIZONTAL);
                Iterator<String> keys = data.keys(); int count = 0;
                while(keys.hasNext() && count < 12) {
                    JSONObject x = data.optJSONObject(keys.next());
                    if (x == null || !"approved".equalsIgnoreCase(x.optString("status"))) continue;
                    TextView item = text(x.optInt("robuxAmount") + " R$\n" + x.optString("username", "user"), 11, Color.WHITE, Typeface.BOLD);
                    item.setGravity(Gravity.CENTER);
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(104), dp(48)); lp.setMargins(dp(4), dp(4), dp(4), dp(4));
                    item.setLayoutParams(lp);
                    item.setBackground(round(Color.argb(42,101,255,178), dp(16), Color.argb(55,101,255,178)));
                    row.addView(item); count++;
                }
                if (count == 0) c.addView(text("No verified redeems yet.", 12, Color.WHITE, Typeface.NORMAL));
                else { HorizontalScrollView hsv = new HorizontalScrollView(MainActivity.this); hsv.setHorizontalScrollBarEnabled(false); hsv.addView(row); c.addView(hsv); }
            }
            public void fail(String error){ c.addView(text("Failed to load recent redeems.", 12, Color.WHITE, Typeface.NORMAL)); }
        });
    }

    private void showProfile() {
        clear(); updateTop();
        LinearLayout c = card();
        c.addView(text("Profile", 28, Color.WHITE, Typeface.BOLD));
        c.addView(text(currentUser, 24, Color.rgb(255,230,109), Typeface.BOLD));
        c.addView(text("Roblox: @" + userData.optString("robloxUsername", "none"), 15, Color.WHITE, Typeface.NORMAL));
        c.addView(text("Coins: " + fmt(num(userData, "coins")), 15, Color.WHITE, Typeface.NORMAL));
        c.addView(text("Total mined: " + fmt(userData.optLong("totalCoinsMined", 0)), 15, Color.WHITE, Typeface.NORMAL));
        c.addView(text("Blocks: " + fmt(userData.optLong("blocksFound", 0)), 15, Color.WHITE, Typeface.NORMAL));
        contentRoot.addView(c);
    }

    private void showOwner() {
        if (!isOwner()) { toast("Owner only"); return; }
        clear(); updateTop();
        final LinearLayout c = card();
        c.addView(text("Owner Panel", 28, Color.WHITE, Typeface.BOLD));
        Button withdraws = btn("Withdraw Orders");
        c.addView(withdraws); contentRoot.addView(c);
        withdraws.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){ loadOwnerWithdraws(c); }});
        loadOwnerWithdraws(c);
    }

    private void loadOwnerWithdraws(final LinearLayout c) {
        c.removeAllViews(); c.addView(text("Withdraw Orders", 28, Color.WHITE, Typeface.BOLD)); c.addView(text("Loading...", 12, Color.WHITE, Typeface.NORMAL));
        get("withdrawals", new JsonCallback(){
            public void done(JSONObject data){
                c.removeAllViews(); c.addView(text("Withdraw Orders", 28, Color.WHITE, Typeface.BOLD));
                if (data == null) { c.addView(text("No orders.", 12, Color.WHITE, Typeface.NORMAL)); return; }
                Iterator<String> keys = data.keys(); boolean any = false;
                while(keys.hasNext()) {
                    final String id = keys.next(); final JSONObject x = data.optJSONObject(id);
                    if (x == null || !"pending".equalsIgnoreCase(x.optString("status", "pending"))) continue;
                    any = true;
                    LinearLayout order = card();
                    order.addView(text(x.optString("username") + " • " + x.optInt("robuxAmount") + " Robux", 16, Color.rgb(255,230,109), Typeface.BOLD));
                    order.addView(text("Roblox: @" + x.optString("robloxUsername") + "\nExtra: " + x.optString("extraInfo", "None"), 12, Color.WHITE, Typeface.NORMAL));
                    Button approve = btn("Approve"); Button deny = btn("Deny");
                    order.addView(approve); order.addView(deny); c.addView(order);
                    approve.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){ ownerSetStatus(id, "approved", c); }});
                    deny.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){ ownerSetStatus(id, "denied", c); }});
                }
                if (!any) c.addView(text("No pending orders.", 12, Color.WHITE, Typeface.NORMAL));
            }
            public void fail(String error){ toast("Owner load failed"); }
        });
    }

    private void ownerSetStatus(String id, String status, final LinearLayout c) {
        try {
            put("withdrawals/" + id + "/status", status, new TextCallback(){
                public void done(String s){ toast("Updated"); loadOwnerWithdraws(c); }
                public void fail(String error){ toast("Update failed"); }
            });
        } catch(Exception e) { toast("Update failed"); }
    }

    private void logout() {
        stopMining();
        currentUser = ""; userData = null; selectedRoblox = null;
        showLogin();
    }
}
