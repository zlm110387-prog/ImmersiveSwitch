package io.github.zlm110387.immersiveswitch;

import android.app.Activity;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import rikka.shizuku.Shizuku;

public class MainActivity extends Activity {
    private static final String TAG = "ImmersiveSwitch";
    private TextView shizukuStatus, stateText, detail;
    private Switch toggle;
    private boolean busy, requested, suppressToggle, hidden;
    private int primary, secondary, card;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private final Shizuku.OnBinderReceivedListener received = () -> runOnUiThread(() -> refresh(true));
    private final Shizuku.OnBinderDeadListener dead = () -> runOnUiThread(() -> refresh(false));
    private final Shizuku.OnRequestPermissionResultListener permission = (code, grant) -> {
        if (code == 1) runOnUiThread(() -> refresh(false));
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        boolean dark = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
            == Configuration.UI_MODE_NIGHT_YES;
        primary = dark ? Color.rgb(242,242,247) : Color.rgb(30,31,35);
        secondary = dark ? Color.rgb(170,170,178) : Color.rgb(108,108,116);
        card = dark ? Color.rgb(30,30,33) : Color.WHITE;
        int bg = dark ? Color.rgb(18,18,20) : Color.rgb(247,247,249);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(bg);
        if (!dark) getWindow().getDecorView().setSystemUiVisibility(
            android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        buildUi(bg);
        Shizuku.addRequestPermissionResultListener(permission);
        Shizuku.addBinderDeadListener(dead);
        Shizuku.addBinderReceivedListenerSticky(received);
        refresh(true);
    }

    private int dp(int v){ return (int)(v*getResources().getDisplayMetrics().density+.5f); }
    private GradientDrawable rounded(int color,int radius){
        GradientDrawable d=new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius)); return d;
    }
    private TextView text(String value,float size,int color,boolean bold){
        TextView t=new TextView(this); t.setText(value); t.setTextSize(size); t.setTextColor(color);
        if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD); return t;
    }

    private void buildUi(int bg) {
        LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(bg); root.setPadding(dp(24),dp(18),dp(24),dp(24));
        root.setOnApplyWindowInsetsListener((v,i)->{
            v.setPadding(dp(24)+i.getSystemWindowInsetLeft(),dp(18)+i.getSystemWindowInsetTop(),
                dp(24)+i.getSystemWindowInsetRight(),dp(24)+i.getSystemWindowInsetBottom()); return i;
        });

        TextView title=text(getString(R.string.app_name),30,primary,true); root.addView(title);
        TextView sub=text(getString(R.string.subtitle),15,secondary,false);
        LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,-2); sp.topMargin=dp(4); root.addView(sub,sp);

        LinearLayout status=new LinearLayout(this); status.setOrientation(LinearLayout.VERTICAL);
        status.setPadding(dp(20),dp(18),dp(20),dp(18)); status.setBackground(rounded(card,22));
        LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2); cp.topMargin=dp(26); root.addView(status,cp);
        status.addView(text(getString(R.string.shizuku_label),13,secondary,false));
        shizukuStatus=text("",17,primary,true); LinearLayout.LayoutParams ss=new LinearLayout.LayoutParams(-1,-2);
        ss.topMargin=dp(5); status.addView(shizukuStatus,ss);

        LinearLayout swCard=new LinearLayout(this); swCard.setOrientation(LinearLayout.HORIZONTAL);
        swCard.setGravity(Gravity.CENTER_VERTICAL); swCard.setPadding(dp(20),dp(20),dp(14),dp(20));
        swCard.setBackground(rounded(card,22)); LinearLayout.LayoutParams wp=new LinearLayout.LayoutParams(-1,-2);
        wp.topMargin=dp(14); root.addView(swCard,wp);
        LinearLayout labels=new LinearLayout(this); labels.setOrientation(LinearLayout.VERTICAL);
        swCard.addView(labels,new LinearLayout.LayoutParams(0,-2,1f));
        labels.addView(text(getString(R.string.switch_title),18,primary,true));
        stateText=text("",14,secondary,false); LinearLayout.LayoutParams stp=new LinearLayout.LayoutParams(-1,-2);
        stp.topMargin=dp(5); labels.addView(stateText,stp);
        toggle=new Switch(this); toggle.setShowText(false); toggle.setMinWidth(dp(58));
        swCard.addView(toggle,new LinearLayout.LayoutParams(dp(64),dp(48)));

        detail=text("",13,secondary,false); detail.setGravity(Gravity.CENTER_HORIZONTAL);
        LinearLayout.LayoutParams dpv=new LinearLayout.LayoutParams(-1,-2); dpv.topMargin=dp(20); root.addView(detail,dpv);
        TextView footer=text(getString(R.string.footer),12,secondary,false); footer.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams fp=new LinearLayout.LayoutParams(-1,-2); fp.weight=1; fp.gravity=Gravity.BOTTOM;
        root.addView(footer,fp);

        setContentView(root);
        toggle.setOnCheckedChangeListener(this::onToggleChanged);
    }

    private void onToggleChanged(CompoundButton b,boolean checked){ if(!suppressToggle&&!busy) execute(checked); }
    @Override protected void onResume(){ super.onResume(); if(shizukuStatus!=null) refresh(true); }

    private void refresh(boolean ask){
        if(isDestroyed())return; boolean ready=false;
        try{
            if(!Shizuku.pingBinder()) shizukuStatus.setText(R.string.waiting_short);
            else if(Shizuku.getVersion()<13) shizukuStatus.setText(R.string.old_version);
            else if(Shizuku.checkSelfPermission()!=PackageManager.PERMISSION_GRANTED){
                shizukuStatus.setText(R.string.denied_short);
                if(ask&&!requested&&!Shizuku.shouldShowRequestPermissionRationale()){requested=true;Shizuku.requestPermission(1);}
            } else if(Shizuku.getVersion()==13){ready=true;shizukuStatus.setText(R.string.ready_short);detectState();}
            else shizukuStatus.setText(R.string.unsupported_version);
        }catch(RuntimeException e){Log.e(TAG,"state",e);shizukuStatus.setText(R.string.error_short);}
        toggle.setEnabled(ready&&!busy);
        if(!ready){stateText.setText(R.string.unavailable);detail.setText(R.string.permission_hint);}
    }

    private void detectState(){
        if(busy)return; executor.execute(()->{try{Boolean d=ShizukuCommandCompat.isHidden();
            if(d!=null)handler.post(()->applyState(d,false));}catch(Exception e){Log.d(TAG,"detect",e);}});
    }
    private void applyState(boolean h,boolean announce){
        if(isDestroyed())return; hidden=h;suppressToggle=true;toggle.setChecked(h);suppressToggle=false;
        stateText.setText(h?R.string.state_hidden:R.string.state_visible);
        if(announce){detail.setText(h?R.string.hidden:R.string.restored); detail.setAlpha(0f); detail.animate().alpha(1f).setDuration(180).start();}
        else if(!busy)detail.setText(R.string.tap_hint);
    }
    private void execute(boolean h){
        busy=true;toggle.setEnabled(false);stateText.setText(R.string.running);detail.setText("");
        executor.execute(()->{String error;try{String o=ShizukuCommandCompat.setHidden(h);
            error=o.startsWith("ERROR:")?o.substring(6):"";}catch(Exception e){Log.e(TAG,"command",e);error=e.toString();}
            String diag=error;handler.post(()->{if(isDestroyed())return;busy=false;
                if(diag.isEmpty())applyState(h,true);else{suppressToggle=true;toggle.setChecked(hidden);suppressToggle=false;detail.setText(getString(R.string.failed,diag));}
                refresh(false);});});
    }
    @Override protected void onDestroy(){
        Shizuku.removeBinderReceivedListener(received);Shizuku.removeBinderDeadListener(dead);
        Shizuku.removeRequestPermissionResultListener(permission);executor.shutdown();super.onDestroy();
    }
}