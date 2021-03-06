package edu.temple.chatapplication;

import java.util.ArrayList;

import android.app.Activity;
import android.content.Context;
import android.support.constraint.ConstraintLayout;
import android.support.v7.widget.RecyclerView;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

public class ChatAdapter extends BaseAdapter {
    private Context context;
    private final ArrayList<Message> messages;

    //layout objects
    TextView messageTV;
    ListView messageListView;
    LinearLayout chatLayout;
    ConstraintLayout parentLayout;

    public ChatAdapter(Context context, ArrayList<Message> messages) {
        this.messages = messages;
        this.context = context;
    }

    @Override
    public int getCount() {
        return (messages != null ? messages.size() : 0);
    }

    @Override
    public Message getItem(int position) {
        return (messages != null ? messages.get(position) : null);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        LayoutInflater layoutInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        convertView = layoutInflater.inflate(R.layout.message_layout, null);
        Message thisMessage = getItem(position);

        messageTV = convertView.findViewById(R.id.message_tv);
        messageListView = convertView.findViewById(R.id.message_list);
        chatLayout = convertView.findViewById(R.id.message_holder);
        parentLayout = convertView.findViewById(R.id.constraint_layout);

        //set text on right if it's user's message
        LinearLayout.LayoutParams layoutParams = (LinearLayout.LayoutParams) chatLayout.getLayoutParams();
        if (thisMessage.belongsToThisUser()) {
            layoutParams.gravity = Gravity.RIGHT;
        } else {
            layoutParams.gravity = Gravity.LEFT;
        }
        chatLayout.setLayoutParams(layoutParams);
        messageTV.setText(thisMessage.getData());




        return convertView;
    }

    public void addMessage(Message decryptedMessage) {
        messages.add(decryptedMessage);
    }
}