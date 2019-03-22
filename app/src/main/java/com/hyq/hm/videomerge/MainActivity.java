package com.hyq.hm.videomerge;

import android.content.Context;
import android.content.Intent;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;

import com.hm.videoedit.holder.VideoHolder;
import com.hyq.hm.videomerge.activity.ListActivity;
import com.hyq.hm.videomerge.video.VideoDecoder;
import com.hyq.hm.videomerge.video.VideoEncode;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    //要编辑的视频数组
    private List<VideoHolder> list = new ArrayList<>();

    private ListAdapter adapter;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ListView listView = findViewById(R.id.list_view);

        adapter = new ListAdapter(this);
        listView.setAdapter(adapter);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if(position == list.size()){
                    Intent intent = new Intent(MainActivity.this,ListActivity.class);
                    startActivityForResult(intent,10001);
                }else{

                }
            }
        });

    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if(resultCode == RESULT_OK){
            if(requestCode == 10001){
                VideoHolder videoHolder = data.getParcelableExtra("videoHolder");
                list.add(videoHolder);
                adapter.notifyDataSetChanged();
            }
        }
        super.onActivityResult(requestCode, resultCode, data);

    }

    private class ListAdapter extends BaseAdapter {
        private Context context;
        private ListAdapter(Context context){
            this.context = context;
        }

        @Override
        public int getCount() {
            return list.size()+1;
        }

        @Override
        public Object getItem(int position) {
            return null;
        }

        @Override
        public long getItemId(int position) {
            return 0;
        }

        @Override
        public int getViewTypeCount() {
            return 2;
        }

        @Override
        public int getItemViewType(int position) {
            if(position == list.size()){
                return 1;
            }
            return 0;
        }

        @Override
        public View getView(final int position, View convertView, ViewGroup parent) {
            if(position == list.size()){
                if(convertView == null){
                    convertView = LayoutInflater.from(context).inflate(R.layout.item_add, parent,
                            false);
                }
            }else{

                ViewHolder viewHolder;

                if(convertView == null){
                    convertView = LayoutInflater.from(context).inflate(R.layout.item_audio, parent,
                            false);
                    viewHolder = new ViewHolder();
                    viewHolder.name = convertView.findViewById(R.id.name_text_view);
                    viewHolder.start = convertView.findViewById(R.id.start_text_view);
                    viewHolder.end = convertView.findViewById(R.id.end_text_view);
                    viewHolder.play = convertView.findViewById(R.id.play_text_view);
                    convertView.setTag(viewHolder);
                }else{
                    viewHolder = (ViewHolder) convertView.getTag();
                }

                VideoHolder videoHolder = list.get(position);
                viewHolder.name.setText(videoHolder.getVideoFile().substring(videoHolder.getVideoFile().lastIndexOf("/")+1));
                viewHolder.start.setText(videoHolder.getStartTime()/1000000+"");
                viewHolder.end.setText(videoHolder.getEndTime()/1000000+"");
                viewHolder.play.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        list.remove(position);
                        adapter.notifyDataSetChanged();
                    }
                });
            }
            return convertView;
        }
        private class ViewHolder{
            TextView name;
            TextView start;
            TextView end;
            TextView play;
        }
    }

    //开始编辑视频
    //没添加音频所以暂时没写进度条，以后和音频一起补上
    public void onConfirm(View view){
        VideoEncode encode = new VideoEncode(list);
        //生成的视频文件
        String path = Environment.getExternalStorageDirectory().getAbsolutePath() +"/HMSDK/video/VideoEdit.mp4";
        File f = new File(path);
        if(f.exists()){
            f.delete();
        }
        encode.start(path);
    }
    public void onNot(View view){

    }
}
