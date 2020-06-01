package com.hyq.hm.videomerge.activity;

import android.Manifest;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.PermissionChecker;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;


import com.hm.videoedit.activity.VideoEditActivity;
import com.hyq.hm.videomerge.R;

import java.util.ArrayList;
import java.util.List;

/**
 * 音频选择Activity
 * Created by 海米 on 2018/10/16.
 */

public class ListActivity extends AppCompatActivity {


    private String[] denied;
    private String[] permissions = {Manifest.permission.WRITE_EXTERNAL_STORAGE};


    private List<String> videoFiles = new ArrayList<>();
    private List<String> videoNames = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_list);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowHomeEnabled(true);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            ArrayList<String> list = new ArrayList<>();
            for (int i = 0; i < permissions.length; i++) {
                if (PermissionChecker.checkSelfPermission(this, permissions[i]) == PackageManager.PERMISSION_DENIED) {
                    list.add(permissions[i]);
                }
            }
            if (list.size() != 0) {
                denied = new String[list.size()];
                for (int i = 0; i < list.size(); i++) {
                    denied[i] = list.get(i);
                    ActivityCompat.requestPermissions(this, denied, 5);
                }

            } else {
                init();
            }
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if(item.getItemId() == android.R.id.home){
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == 5) {
            boolean isDenied = false;
            for (int i = 0; i < denied.length; i++) {
                String permission = denied[i];
                for (int j = 0; j < permissions.length; j++) {
                    if (permissions[j].equals(permission)) {
                        if (grantResults[j] != PackageManager.PERMISSION_GRANTED) {
                            isDenied = true;
                            break;
                        }
                    }
                }
            }
            if (isDenied) {
                Toast.makeText(this, "请开启权限", Toast.LENGTH_SHORT).show();
            } else {
                init();

            }
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    private void init(){
        Thread thread = new Thread(){
            @Override
            public void run() {
                super.run();
                ContentResolver cr = getContentResolver();
                Cursor cursor = cr.query(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                        null, null, null, MediaStore.Video.Media.DEFAULT_SORT_ORDER);

                if(cursor != null){
                    if(cursor.moveToFirst()){
                        while (!cursor.isAfterLast()){
                            String fileName = cursor
                                    .getString(cursor
                                            .getColumnIndex(MediaStore.Video.Media.DISPLAY_NAME));
                            String path = cursor.getString(cursor
                                    .getColumnIndex(MediaStore.Video.Media.DATA));
                            videoNames.add(fileName);
                            videoFiles.add(path);
                            cursor.moveToNext();
                        }
                    }
                }
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        RecyclerView recyclerView = findViewById(R.id.recycler_view);
                        GridLayoutManager manager = new GridLayoutManager(ListActivity.this,4);
                        recyclerView.setLayoutManager(manager);
                        PathAdapter adapter = new PathAdapter(ListActivity.this);
                        recyclerView.setAdapter(adapter);
                    }
                });
            }
        };
        thread.start();


    }
    class PathAdapter extends RecyclerView.Adapter<PathAdapter.ViewHolder>{
        private Context context;
        public PathAdapter(Context context){
            this.context = context;
        }
        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {


            return new ViewHolder(LayoutInflater.from(context).inflate(R.layout.item_list, parent,
                    false));
        }

        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            holder.position = position;
            holder.textView.setText(videoNames.get(position));
        }

        @Override
        public int getItemCount() {
            return videoNames.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {

            TextView textView;
            int position;
            public ViewHolder(View view)
            {
                super(view);
                textView = view.findViewById(R.id.name_text_view);
                textView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Intent intent = new Intent(ListActivity.this,VideoEditActivity.class);
                        intent.putExtra("path",videoFiles.get(position));
                        intent.putExtra("name",videoNames.get(position));
                        startActivityForResult(intent,10001);
                    }
                });
            }
        }
    }
    //error: redeclaration of 'strchr' must have the 'overloadable' attribute
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if(resultCode == RESULT_OK){
            if(requestCode == 10001){
                setResult(RESULT_OK,data);
                finish();
            }
        }
        super.onActivityResult(requestCode, resultCode, data);

    }
}
