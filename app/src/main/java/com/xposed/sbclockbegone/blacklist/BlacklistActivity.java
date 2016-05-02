package com.xposed.sbclockbegone.blacklist;

import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Checkable;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import com.xposed.sbclockbegone.BuildConfig;
import com.xposed.sbclockbegone.R;
import com.xposed.sbclockbegone.Xposed;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class BlacklistActivity extends AppCompatActivity implements AdapterView.OnItemClickListener {

    @SuppressWarnings("unused")
    private static final String TAG = BlacklistActivity.class.getSimpleName();
    private static final String LAUNCHER_ALIAS = "com.xposed.sbclockbegone.Launcher";
    private static final String PREF_HIDE_ICON = "hide_icon";

    private SharedPreferences mPrefs;
    private InstalledApplicationsAdapter mAdapter;

    @SuppressWarnings({"deprecation", "ConstantConditions"})
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mPrefs = getSharedPreferences(Xposed.PREFERENCES, MODE_WORLD_READABLE);
        mAdapter = new InstalledApplicationsAdapter(this, getPackageManager());
        mAdapter.setApplicationInfos(getLauncherApps(getPackageManager()));

        setContentView(R.layout.activity_blacklist);
        setSupportActionBar((Toolbar) findViewById(R.id.toolbar));
        getSupportActionBar().setHomeAsUpIndicator(R.mipmap.ic_launcher);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        ListView listView = (ListView) findViewById(android.R.id.list);
        listView.setOnItemClickListener(this);
        listView.setAdapter(mAdapter);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_blacklist, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.item_clear_selection:
                new AlertDialog.Builder(this).setCancelable(true)
                        .setTitle(R.string.selection)
                        .setItems(R.array.selection_items, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                List<String> allPackageNames;
                                switch (which) {
                                    case 0:
                                        // Select all
                                        allPackageNames = new ArrayList<>();
                                        for (ApplicationInfo ai : getLauncherApps(getPackageManager())) {
                                            allPackageNames.add(ai.packageName);
                                        }
                                        break;
                                    case 1:
                                        // Select inverse
                                        allPackageNames = new ArrayList<>();
                                        List<String> blacklist = Blacklist.get(BlacklistActivity.this);
                                        for (ApplicationInfo ai : getLauncherApps(getPackageManager())) {
                                            String packageName = ai.packageName;
                                            if (!blacklist.contains(packageName)) {
                                                allPackageNames.add(packageName);
                                            }
                                        }
                                        break;
                                    default:
                                        // Select none
                                        allPackageNames = null;
                                        break;
                                }
                                Blacklist.set(BlacklistActivity.this, allPackageNames);
                                mAdapter.notifyDataSetChanged();

                                sendClockEventBroadcast(null == allPackageNames);
                                dialog.dismiss();
                            }
                        })
                        .setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.dismiss();
                            }
                        })
                        .create().show();
                return true;
            case R.id.item_view_source:
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setData(Uri.parse("https://github.com/Gaelan-Bolger/SBClockBeGone-Android-Xposed"));
                startActivity(intent);
                return true;
            case R.id.item_hide_icon:
                boolean hideIcon = mPrefs.getBoolean(PREF_HIDE_ICON, false);
                setLauncherAliasEnabled(hideIcon);
                mPrefs.edit().putBoolean(PREF_HIDE_ICON, !hideIcon).apply();
                invalidateOptionsMenu();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        menu.findItem(R.id.item_hide_icon).setChecked(mPrefs.getBoolean(PREF_HIDE_ICON, false));
        menu.findItem(R.id.item_app_version).setTitle(getString(R.string.version, BuildConfig.VERSION_NAME));
        return true;
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        ApplicationInfo item = mAdapter.getItem(position);
        String packageName = item.packageName;
        if (Blacklist.contains(this, packageName))
            Blacklist.remove(this, packageName);
        else
            Blacklist.add(this, packageName);
        mAdapter.notifyDataSetChanged();

        if (getPackageName().equals(packageName)) {
            sendClockEventBroadcast(!Blacklist.contains(this, packageName));
        }
    }

    private void sendClockEventBroadcast(boolean showClock) {
        Intent intent = new Intent(Xposed.ACTION_CLOCK_EVENT);
        intent.putExtra(Xposed.EXTRA_SHOW_CLOCK, showClock);
        sendBroadcast(intent);
    }

    private ArrayList<ApplicationInfo> getLauncherApps(PackageManager pm) {
        List<ApplicationInfo> allInstalledApps = pm.getInstalledApplications(0);
        ArrayList<ApplicationInfo> launcherApps = new ArrayList<>();
        for (ApplicationInfo installedApp : allInstalledApps)
            if (null != pm.getLaunchIntentForPackage(installedApp.packageName))
                launcherApps.add(installedApp);
        Collections.sort(launcherApps, new ApplicationInfo.DisplayNameComparator(pm));
        return launcherApps;
    }

    private void setLauncherAliasEnabled(boolean enabled) {
        int mode = enabled ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED :
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
        getPackageManager().setComponentEnabledSetting(new ComponentName(this, LAUNCHER_ALIAS),
                mode, PackageManager.DONT_KILL_APP);
    }

    private class InstalledApplicationsAdapter extends BaseAdapter {

        private final Context context;
        private final PackageManager pm;
        private List<ApplicationInfo> applicationInfos;

        public InstalledApplicationsAdapter(Context context, PackageManager pm) {
            this.context = context;
            this.pm = pm;
        }

        @Override
        public int getCount() {
            return null != applicationInfos ? applicationInfos.size() : 0;
        }

        @Override
        public ApplicationInfo getItem(int position) {
            return applicationInfos.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            Holder h;
            if (null == convertView) {
                convertView = LayoutInflater.from(context).inflate(R.layout.application_list_item, parent, false);
                h = new Holder(convertView);
            } else {
                h = (Holder) convertView.getTag();
            }

            ApplicationInfo item = getItem(position);
            h.itemView.setChecked(Blacklist.contains(context, item.packageName));
            h.icon.setImageDrawable(item.loadIcon(pm));
            h.text.setText(item.loadLabel(pm));
            return convertView;
        }

        public void setApplicationInfos(ArrayList<ApplicationInfo> applicationInfos) {
            this.applicationInfos = applicationInfos;
        }

        private class Holder {

            private final Checkable itemView;
            private final ImageView icon;
            private final TextView text;

            public Holder(View convertView) {
                convertView.setTag(this);
                itemView = (Checkable) convertView;
                icon = (ImageView) convertView.findViewById(android.R.id.icon);
                text = (TextView) convertView.findViewById(android.R.id.text1);
            }
        }
    }
}
