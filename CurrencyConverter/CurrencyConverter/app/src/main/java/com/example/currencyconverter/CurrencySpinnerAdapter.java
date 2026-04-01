package com.example.currencyconverter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

public class CurrencySpinnerAdapter extends BaseAdapter {

    private final Context context;
    private final String[] codes;
    private final String[] names;
    private final String[] flags;

    public CurrencySpinnerAdapter(Context context, String[] codes,
                                   String[] names, String[] flags) {
        this.context = context;
        this.codes   = codes;
        this.names   = names;
        this.flags   = flags;
    }

    @Override public int getCount()              { return codes.length; }
    @Override public Object getItem(int pos)     { return codes[pos]; }
    @Override public long getItemId(int pos)     { return pos; }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        return buildView(position, convertView, parent, R.layout.item_spinner_selected);
    }

    @Override
    public View getDropDownView(int position, View convertView, ViewGroup parent) {
        return buildView(position, convertView, parent, R.layout.item_spinner_dropdown);
    }

    private View buildView(int position, View convertView, ViewGroup parent, int layoutId) {
        if (convertView == null) {
            convertView = LayoutInflater.from(context).inflate(layoutId, parent, false);
        }
        TextView tvFlag = convertView.findViewById(R.id.tvFlag);
        TextView tvCode = convertView.findViewById(R.id.tvCode);
        TextView tvName = convertView.findViewById(R.id.tvName);

        if (tvFlag != null) tvFlag.setText(flags[position]);
        tvCode.setText(codes[position]);
        if (tvName != null) tvName.setText(names[position]);

        return convertView;
    }
}
