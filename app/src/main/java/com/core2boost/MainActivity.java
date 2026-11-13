package com.core2boost;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Environment;
import android.os.StatFs;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class MainActivity extends Activity {
    private static final int BG = Color.rgb(16,17,22);
    private static final int PANEL = Color.rgb(29,32,41);
    private static final int WHITE = Color.rgb(242,244,248);
    private static final int MUTED = Color.rgb(148,155,169);
    private static final int ACCENT = Color.rgb(100,230,214);
    private static final String PREFS = "core2boost";
    private LinearLayout main, appList, alphabetRail, favoritesBox;
    private EditText searchBox;
    private TextView rootLabel, memoryLabel, storageLabel, appCountLabel;
    private ArrayList<PackageInfo> allApps = new ArrayList<PackageInfo>();
    private ArrayList<PackageInfo> shownApps = new ArrayList<PackageInfo>();
    private Set<String> favorites = new HashSet<String>();
    private boolean rootOk = false;
    private boolean showSystem = false;
    private boolean drawerVisible = false;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        SharedPreferences p = getSharedPreferences(PREFS, MODE_PRIVATE);
        favorites = p.getStringSet("favorites", new HashSet<String>());
        favorites = new HashSet<String>(favorites);
        buildScreen();
        detectRoot();
        refreshDeviceStats();
        loadApplications();
    }

    private int dp(int n) {
        return (int)(n * getResources().getDisplayMetrics().density + 0.5f);
    }

    private GradientDrawable rounded(int color, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radius));
        return d;
    }

    private TextView makeText(String value, int size, int color) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        t.setGravity(Gravity.CENTER_VERTICAL);
        return t;
    }

    private void buildScreen() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        main = new LinearLayout(this);
        main.setOrientation(LinearLayout.VERTICAL);
        main.setPadding(dp(20), dp(18), dp(14), dp(28));
        main.setBackgroundColor(BG);
        scroll.addView(main);
        setContentView(scroll);

        TextView brand = makeText("CORE 2 BOOST", 12, ACCENT);
        brand.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        main.addView(brand, new LinearLayout.LayoutParams(-1, dp(25)));

        TextView clock = makeText(android.text.format.DateFormat.format("HH:mm", System.currentTimeMillis()).toString(), 42, WHITE);
        clock.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        main.addView(clock);
        TextView date = makeText(android.text.format.DateFormat.format("EEEE, d MMMM", System.currentTimeMillis()).toString(), 13, MUTED);
        main.addView(date);

        LinearLayout stats = new LinearLayout(this);
        stats.setOrientation(LinearLayout.HORIZONTAL);
        stats.setPadding(0, dp(14), 0, dp(4));
        memoryLabel = makeText("RAM  ·  --", 11, MUTED);
        storageLabel = makeText("ALMACENAMIENTO  ·  --", 11, MUTED);
        stats.addView(memoryLabel, new LinearLayout.LayoutParams(0, dp(30), 1));
        stats.addView(storageLabel, new LinearLayout.LayoutParams(0, dp(30), 1));
        main.addView(stats);

        rootLabel = makeText("Verificando acceso ROOT…", 11, MUTED);
        main.addView(rootLabel, new LinearLayout.LayoutParams(-1, dp(28)));

        searchBox = new EditText(this);
        searchBox.setSingleLine(true);
        searchBox.setTextSize(14);
        searchBox.setTextColor(WHITE);
        searchBox.setHintTextColor(MUTED);
        searchBox.setHint("⌕   Buscar aplicación");
        searchBox.setPadding(dp(15), 0, dp(15), 0);
        searchBox.setBackground(rounded(PANEL, 25));
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1, dp(46));
        sp.topMargin = dp(12);
        main.addView(searchBox, sp);
        searchBox.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) { renderApps(s.toString()); }
            public void afterTextChanged(Editable s) {}
        });

        TextView favTitle = makeText("TUS FAVORITOS", 10, ACCENT);
        favTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        LinearLayout.LayoutParams ftp = new LinearLayout.LayoutParams(-1, dp(38));
        ftp.topMargin = dp(10);
        main.addView(favTitle, ftp);
        favoritesBox = new LinearLayout(this);
        favoritesBox.setOrientation(LinearLayout.VERTICAL);
        main.addView(favoritesBox);

        LinearLayout section = new LinearLayout(this);
        section.setGravity(Gravity.CENTER_VERTICAL);
        TextView sectionTitle = makeText("TODAS LAS APLICACIONES", 10, ACCENT);
        sectionTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        section.addView(sectionTitle, new LinearLayout.LayoutParams(0, dp(42), 1));
        Button sys = new Button(this);
        sys.setText("SISTEMA");
        sys.setTextSize(9);
        sys.setTextColor(ACCENT);
        sys.setBackgroundColor(Color.TRANSPARENT);
        sys.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                showSystem = !showSystem;
                loadApplications();
            }
        });
        section.addView(sys);
        main.addView(section);

        LinearLayout drawer = new LinearLayout(this);
        drawer.setOrientation(LinearLayout.HORIZONTAL);
        appList = new LinearLayout(this);
        appList.setOrientation(LinearLayout.VERTICAL);
        drawer.addView(appList, new LinearLayout.LayoutParams(0, -2, 1));
        alphabetRail = new LinearLayout(this);
        alphabetRail.setOrientation(LinearLayout.VERTICAL);
        alphabetRail.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        drawer.addView(alphabetRail, new LinearLayout.LayoutParams(dp(24), -2));
        main.addView(drawer);

        appCountLabel = makeText("", 10, MUTED);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, dp(35));
        cp.topMargin = dp(8);
        main.addView(appCountLabel, cp);

        TextView foot = makeText("CORE 2 BOOST  ·  NIAGARA-INSPIRED  ·  KITKAT", 9, MUTED);
        main.addView(foot);
    }

    private void detectRoot() {
        new Thread(new Runnable() {
            public void run() {
                final boolean ok = rootCommand("id").indexOf("uid=0") >= 0;
                runOnUiThread(new Runnable() {
                    public void run() {
                        rootOk = ok;
                        rootLabel.setText(ok ? "● ROOT CONCEDIDO  ·  GESTIÓN SEGURA" : "ROOT NO DISPONIBLE  ·  MODO LANZADOR");
                        rootLabel.setTextColor(ok ? ACCENT : MUTED);
                    }
                });
            }
        }).start();
    }

    private String rootCommand(String command) {
        StringBuilder result = new StringBuilder();
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"su", "-c", command});
            BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line;
            while ((line = r.readLine()) != null) result.append(line).append("\n");
            p.waitFor();
        } catch (Exception e) {}
        return result.toString();
    }

    private void refreshDeviceStats() {
        try {
            ActivityManager am = (ActivityManager)getSystemService(ACTIVITY_SERVICE);
            ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
            am.getMemoryInfo(mi);
            memoryLabel.setText("RAM LIBRE  ·  " + (mi.availMem / 1048576L) + " MB");
        } catch (Exception e) { memoryLabel.setText("RAM  ·  no disponible"); }
        try {
            StatFs fs = new StatFs(Environment.getDataDirectory().getPath());
            long block = fs.getBlockSize();
            long available = ((long)fs.getAvailableBlocks()) * block / 1048576L;
            storageLabel.setText("LIBRE  ·  " + available + " MB");
        } catch (Exception e) { storageLabel.setText("ESPACIO  ·  no disponible"); }
    }

    private void loadApplications() {
        PackageManager pm = getPackageManager();
        List<PackageInfo> packages = pm.getInstalledPackages(PackageManager.GET_DISABLED_COMPONENTS);
        allApps.clear();
        for (PackageInfo p : packages) {
            boolean system = (p.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
            boolean launchable = pm.getLaunchIntentForPackage(p.packageName) != null;
            if ((showSystem || !system) && (launchable || showSystem)) allApps.add(p);
        }
        Collections.sort(allApps, new Comparator<PackageInfo>() {
            public int compare(PackageInfo a, PackageInfo b) {
                return appName(a).compareToIgnoreCase(appName(b));
            }
        });
        renderFavorites();
        renderApps(searchBox == null ? "" : searchBox.getText().toString());
    }

    private String appName(PackageInfo p) {
        CharSequence c = p.applicationInfo.loadLabel(getPackageManager());
        return c == null ? p.packageName : c.toString();
    }

    private void renderFavorites() {
        favoritesBox.removeAllViews();
        int count = 0;
        for (int i = 0; i < allApps.size(); i++) {
            final PackageInfo p = allApps.get(i);
            if (favorites.contains(p.packageName)) {
                addAppRow(favoritesBox, p, true);
                count++;
            }
        }
        if (count == 0) {
            TextView hint = makeText("Mantén pulsada una app para añadirla aquí", 11, MUTED);
            favoritesBox.addView(hint, new LinearLayout.LayoutParams(-1, dp(28)));
        }
    }

    private void renderApps(String query) {
        if (appList == null) return;
        appList.removeAllViews();
        alphabetRail.removeAllViews();
        shownApps.clear();
        String q = query == null ? "" : query.toLowerCase(Locale.getDefault()).trim();
        for (PackageInfo p : allApps) {
            String name = appName(p);
            if (name.toLowerCase(Locale.getDefault()).contains(q) ||
                p.packageName.toLowerCase(Locale.getDefault()).contains(q)) shownApps.add(p);
        }

        String previous = "";
        for (int i = 0; i < shownApps.size(); i++) {
            PackageInfo p = shownApps.get(i);
            String first = appName(p).substring(0,1).toUpperCase(Locale.getDefault());
            if (!first.equals(previous)) {
                TextView letter = makeText(first, 10, ACCENT);
                letter.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
                appList.addView(letter, new LinearLayout.LayoutParams(-1, dp(26)));
                previous = first;
            }
            addAppRow(appList, p, false);
        }

        if (shownApps.size() == 0) {
            appList.addView(makeText("No hay resultados", 13, MUTED));
        }
        appCountLabel.setText(shownApps.size() + " aplicaciones · " + (showSystem ? "incluye sistema" : "aplicaciones de usuario"));

        if (q.length() == 0 && shownApps.size() > 0) {
            String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
            for (int i = 0; i < alphabet.length(); i++) {
                final String letter = alphabet.substring(i, i+1);
                TextView index = makeText(letter, 9, MUTED);
                index.setGravity(Gravity.CENTER);
                alphabetRail.addView(index, new LinearLayout.LayoutParams(-1, dp(18)));
                index.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) { jumpToLetter(letter); }
                });
            }
        }
    }

    private void jumpToLetter(String letter) {
        for (int i = 0; i < appList.getChildCount(); i++) {
            View v = appList.getChildAt(i);
            if (v instanceof TextView) {
                String value = ((TextView)v).getText().toString();
                if (value.equals(letter)) {
                    v.requestFocus();
                    return;
                }
            }
        }
        Toast.makeText(this, "No hay aplicaciones con " + letter, Toast.LENGTH_SHORT).show();
    }

    private void addAppRow(LinearLayout parent, final PackageInfo p, boolean favoriteRow) {
        final String name = appName(p);
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(2), 0, dp(4), 0);
        TextView icon = makeText(name.substring(0,1).toUpperCase(Locale.getDefault()), 16, ACCENT);
        icon.setGravity(Gravity.CENTER);
        icon.setBackground(rounded(Color.rgb(38,48,55), 22));
        row.addView(icon, new LinearLayout.LayoutParams(dp(34), dp(34)));
        TextView title = makeText("   " + name, 14, WHITE);
        row.addView(title, new LinearLayout.LayoutParams(0, dp(48), 1));
        TextView star = makeText(favorites.contains(p.packageName) ? "★" : "›", 17,
                favorites.contains(p.packageName) ? ACCENT : MUTED);
        row.addView(star);
        row.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { showAppMenu(p); }
        });
        row.setOnLongClickListener(new View.OnLongClickListener() {
            public boolean onLongClick(View v) {
                toggleFavorite(p.packageName);
                return true;
            }
        });
        parent.addView(row);
        View divider = new View(this);
        divider.setBackgroundColor(Color.rgb(39,42,51));
        parent.addView(divider, new LinearLayout.LayoutParams(-1, dp(1)));
    }

    private void toggleFavorite(String pkg) {
        if (favorites.contains(pkg)) favorites.remove(pkg); else favorites.add(pkg);
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putStringSet("favorites", favorites).commit();
        renderFavorites();
        renderApps(searchBox.getText().toString());
        Toast.makeText(this, favorites.contains(pkg) ? "Añadida a favoritos" : "Quitada de favoritos", Toast.LENGTH_SHORT).show();
    }

    private boolean protectedPackage(String pkg) {
        String[] protectedNames = {
            "com.android.phone", "com.android.providers.telephony",
            "com.android.providers.contacts", "com.android.providers.settings",
            "com.android.systemui", "com.android.packageinstaller",
            "com.android.launcher", "com.android.launcher2",
            "com.google.android.gms", "com.google.android.gsf",
            "com.android.providers.downloads", "com.android.bluetooth",
            "com.android.mms", "com.android.dialer"
        };
        for (String s : protectedNames) if (pkg.equals(s)) return true;
        return pkg.startsWith("com.android.server") || pkg.startsWith("com.android.providers");
    }

    private void showAppMenu(final PackageInfo p) {
        final String pkg = p.packageName;
        final String name = appName(p);
        final boolean enabled = p.applicationInfo.enabled;
        final boolean protectedApp = protectedPackage(pkg);
        ArrayList<String> options = new ArrayList<String>();
        options.add("Abrir");
        options.add("Información de la aplicación");
        options.add(favorites.contains(pkg) ? "Quitar de favoritos" : "Añadir a favoritos");
        if (!protectedApp) options.add(enabled ? "Deshabilitar (ROOT)" : "Habilitar (ROOT)");
        new AlertDialog.Builder(this).setTitle(name).setItems(options.toArray(new String[options.size()]),
            new android.content.DialogInterface.OnClickListener() {
                public void onClick(android.content.DialogInterface dialog, int which) {
                    if (which == 0) launch(pkg);
                    else if (which == 1) appInfo(pkg);
                    else if (which == 2) toggleFavorite(pkg);
                    else if (which == 3) confirmPackageToggle(pkg, name, enabled);
                }
            }).show();
    }

    private void confirmPackageToggle(final String pkg, String name, final boolean currentlyEnabled) {
        if (!rootOk) {
            new AlertDialog.Builder(this).setMessage("No se detectó acceso ROOT. No se ha realizado ningún cambio.")
                .setPositiveButton("OK", null).show();
            return;
        }
        final String action = currentlyEnabled ? "deshabilitar" : "habilitar";
        new AlertDialog.Builder(this).setTitle("Confirmar")
            .setMessage("¿Quieres " + action + " " + name + "?\n\nDeshabilitar aplicaciones puede causar fallos. No toques componentes que no reconozcas. La acción es reversible.")
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Continuar", new android.content.DialogInterface.OnClickListener() {
                public void onClick(android.content.DialogInterface d, int w) {
                    String command = (currentlyEnabled ? "pm disable " : "pm enable ") + pkg;
                    runPackageCommand(command);
                }
            }).show();
    }

    private void runPackageCommand(final String command) {
        new Thread(new Runnable() {
            public void run() {
                final String output = rootCommand(command);
                runOnUiThread(new Runnable() {
                    public void run() {
                        String msg = output.length() == 0 ? "Comando enviado. Actualizando lista." : output;
                        new AlertDialog.Builder(MainActivity.this).setMessage(msg)
                            .setPositiveButton("OK", new android.content.DialogInterface.OnClickListener() {
                                public void onClick(android.content.DialogInterface d, int w) { loadApplications(); }
                            }).show();
                    }
                });
            }
        }).start();
    }

    private void launch(String pkg) {
        try {
            Intent i = getPackageManager().getLaunchIntentForPackage(pkg);
            if (i != null) startActivity(i);
            else Toast.makeText(this, "Esta aplicación no tiene pantalla de inicio", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "No se pudo abrir la aplicación", Toast.LENGTH_SHORT).show();
        }
    }

    private void appInfo(String pkg) {
        try {
            Intent i = new Intent("android.settings.APPLICATION_DETAILS_SETTINGS");
            i.setData(android.net.Uri.parse("package:" + pkg));
            startActivity(i);
        } catch (Exception e) {}
    }

    @Override protected void onResume() {
        super.onResume();
        if (rootLabel != null) refreshDeviceStats();
    }

    @Override public void onBackPressed() {
        if (searchBox != null && searchBox.length() > 0) searchBox.setText("");
        else super.onBackPressed();
    }
}
