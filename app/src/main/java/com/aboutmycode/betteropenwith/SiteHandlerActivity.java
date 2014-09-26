package com.aboutmycode.betteropenwith;

import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.text.TextUtils;

import com.aboutmycode.betteropenwith.database.CupboardSQLiteOpenHelper;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

import static nl.qbusict.cupboard.CupboardFactory.cupboard;

public class SiteHandlerActivity extends FileHandlerActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    protected Intent getDetailsScreenIntent(ItemBase item) {
        Intent browserDetailsIntent = new Intent(this, BrowserDetailsActivity.class);
        if (item instanceof Site) {
            browserDetailsIntent.putExtra("siteId", item.getId());
        } else {
            browserDetailsIntent.putExtra("siteId", 0);
        }

        return browserDetailsIntent;
    }

    public String getDomainName(String url) throws URISyntaxException {
        URI uri = new URI(url);
        String domain = uri.getHost();
        return domain.startsWith("www.") ? domain.substring(4) : domain;
    }

    @Override
    protected ItemBase getCurrentItem(Intent intent) {
        List<Site> sites = getSites();
        String url = intent.getDataString();

        for (Site site : sites) {
            try {
                String domainName = getDomainName(url).toLowerCase();
                if (domainName.contains(site.getDomain())) {
                    return site;
                }
            } catch (URISyntaxException e) {
                e.printStackTrace();
            }
        }

        return super.getCurrentItem(intent);
    }

    private List<Site> getSites() {
        CupboardSQLiteOpenHelper dbHelper = new CupboardSQLiteOpenHelper(this);
        SQLiteDatabase database = dbHelper.getReadableDatabase();

        List<Site> list = cupboard().withDatabase(database).query(Site.class).list();

        database.close();
        dbHelper.close();
        return list;
    }
}