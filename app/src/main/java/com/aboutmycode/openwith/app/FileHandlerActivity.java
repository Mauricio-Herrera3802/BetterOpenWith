package com.aboutmycode.openwith.app;

import android.app.ActivityManager;
import android.app.ListActivity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.aboutmycode.openwith.app.common.adapter.CommonAdapter;
import com.aboutmycode.openwith.app.common.adapter.IBindView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;


public class FileHandlerActivity extends ListActivity {
    private Timer autoStart;

    private Button pauseButton;
    private TextView secondsTextView;

    private int elapsed;
    private int timeout;
    private boolean paused;

    private CommonAdapter<ResolveInfoDisplay> adapter;
    private Intent original = new Intent();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (savedInstanceState != null) {
            elapsed = savedInstanceState.getInt("elapsed", 0);
            paused = savedInstanceState.getBoolean("paused", false);
        }

        setContentView(R.layout.file_handler);
        setTitle(getString(R.string.complete_action_with));

        Intent launchIntent = getIntent();

        original = makeMyIntent();

        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(launchIntent.getData(), launchIntent.getType());

        PackageManager packageManager = getPackageManager();
        List<ResolveInfo> resInfo = packageManager.queryIntentActivities(intent, 0);

        //If only one app is found it is us so there is no other app.
        if (resInfo.size() == 1) {
            Toast.makeText(this, "No application found to open the selected file", Toast.LENGTH_LONG).show();
            finish();
        }

        //If there are two apps start the other one.
        if (resInfo.size() == 2) {
            ResolveInfo resolveInfo = resInfo.get(0);

            if (resolveInfo.activityInfo.packageName.equals(getPackageName())) {
                startIntentFromResolveInfo(resInfo.get(1));
            } else {
                startIntentFromResolveInfo(resolveInfo);
            }
            finish();
        }

        Collections.sort(resInfo, new ResolveInfo.DisplayNameComparator(packageManager));

        List<ResolveInfoDisplay> list = new ArrayList<ResolveInfoDisplay>();

        for (ResolveInfo item : resInfo) {
            if (item.activityInfo.packageName.equals(getPackageName())) {
                continue;
            }

            ResolveInfoDisplay resolveInfoDisplay = new ResolveInfoDisplay();
            resolveInfoDisplay.setDisplayLabel(item.loadLabel(packageManager));
            resolveInfoDisplay.setDisplayIcon(item.loadIcon(packageManager));
            resolveInfoDisplay.setResolveInfo(item);

            list.add(resolveInfoDisplay);
        }

        adapter = new CommonAdapter<ResolveInfoDisplay>(this, list, R.layout.resolve_list_item, new ResolveInfoDisplayFileHandlerViewBinder());
        setListAdapter(adapter);

        ListView listView = getListView();
        listView.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
        listView.setItemChecked(0, true);

        secondsTextView = (TextView) findViewById(R.id.secondsTextView);
        pauseButton = (Button) findViewById(R.id.pauseButton);
        pauseButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (paused) {
                    configureTimer();
                    pauseButton.setText("Pause");
                } else {
                    autoStart.cancel();
                    pauseButton.setText("Resume");
                }

                paused = !paused;
                showTimerStatus();
            }
        });

        findViewById(R.id.settingsButton).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent mainScreen = new Intent(FileHandlerActivity.this, HandlerListActivity.class);
                mainScreen.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(mainScreen);
            }
        });
    }

    @Override
    protected void onListItemClick(ListView listView, View v, int position, long id) {
        startSelectedItem(listView, position);
    }

    private void startSelectedItem(ListView listView, int position) {
        ResolveInfoDisplay item = (ResolveInfoDisplay) listView.getItemAtPosition(position);

        ResolveInfo resolveInfo = item.getResolveInfo();
        startIntentFromResolveInfo(resolveInfo);
    }

    private void startIntentFromResolveInfo(ResolveInfo resolveInfo) {
        autoStart.cancel();

        ActivityInfo ai = resolveInfo.activityInfo;

        Intent intent = new Intent(original);
        intent.addFlags(Intent.FLAG_ACTIVITY_FORWARD_RESULT | Intent.FLAG_ACTIVITY_PREVIOUS_IS_TOP);
        intent.setComponent(new ComponentName(ai.applicationInfo.packageName, ai.name));

        startActivity(intent);
        finish();
    }

    private Intent makeMyIntent() {
        Intent intent = new Intent(getIntent());
        intent.setComponent(null);
        intent.setFlags(intent.getFlags() & ~Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
        return intent;
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putInt("elapsed", elapsed);
        outState.putBoolean("paused", paused);
        super.onSaveInstanceState(outState);

        if (isFinishing() || isChangingConfigurations() && autoStart != null) {
            autoStart.cancel();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();

        timeout = PreferenceManager.getDefaultSharedPreferences(this).getInt("timeout", 4);

        showTimerStatus();

        if (autoStart == null && !paused) {
            configureTimer();
        }
    }

    private void showTimerStatus() {
        if (paused) {
            secondsTextView.setText("Paused");
        } else {
            secondsTextView.setText(String.format("Launching in %s seconds", timeout - elapsed));
        }
    }

    private void configureTimer() {
        autoStart = new Timer("AutoStart");
        autoStart.schedule(new TimerTask() {
            @Override
            public void run() {
                elapsed++;

                if (elapsed == timeout) {
                    startSelectedItem(getListView(), 0);
                } else {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            secondsTextView.setText(String.format("Launching in %s seconds", timeout - elapsed));
                        }
                    });
                }
            }
        }, 1000, 1000);
    }

    @Override
    protected void onStop() {
        super.onStop();

        if (isFinishing() && autoStart != null) {
            autoStart.cancel();
        }
    }
}

class ResolveInfoDisplayFileHandlerViewBinder implements IBindView<ResolveInfoDisplay> {

    @Override
    public View bind(View row, ResolveInfoDisplay item, Context context) {
        Object tag = row.getTag();
        if (tag == null) {
            final ViewHolder holder = new ViewHolder(row);
            row.setTag(holder);

            ActivityManager am = (ActivityManager) context.getSystemService(context.ACTIVITY_SERVICE);

            ViewGroup.LayoutParams lp = holder.icon.getLayoutParams();
            lp.width = lp.height = am.getLauncherLargeIconSize();

            tag = holder;
        }

        ViewHolder holder = (ViewHolder) tag;

        holder.text.setText(item.getDisplayLabel());
        holder.icon.setImageDrawable(item.getDisplayIcon());

        return row;
    }
}

class ViewHolder {
    public TextView text;
    public ImageView icon;

    public ViewHolder(View view) {
        text = (TextView) view.findViewById(android.R.id.text1);
        icon = (ImageView) view.findViewById(R.id.icon);
    }
}