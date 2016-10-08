package net.z0kai.refreshlayout.view;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;

import net.z0kai.refreshlayout.R;

/**
 * Created by Z_0Kai on 16/9/29.
 */

public class DefaultFooterView extends RelativeLayout implements IFooterView {

    private ProgressBar loadMorePb;
    private TextView noMoreTv;

    public DefaultFooterView(Context context) {
        this(context, null);
    }

    public DefaultFooterView(Context context, AttributeSet attrs) {
        super(context, attrs);
        LayoutInflater.from(getContext()).inflate(R.layout.kk_rl_default_footer_view, this);
        ViewGroup.LayoutParams lp = new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        setLayoutParams(lp);

        loadMorePb = (ProgressBar) findViewById(R.id.loadMorePb);
        noMoreTv = (TextView) findViewById(R.id.noMoreTv);
    }

    @Override
    public View getView() {
        return this;
    }

    @Override
    public int getSize() {
        return getHeight();
    }

    @Override
    public void showLoading() {
        loadMorePb.setVisibility(VISIBLE);
        noMoreTv.setVisibility(INVISIBLE);
    }

    @Override
    public void showNoMore() {
        loadMorePb.setVisibility(INVISIBLE);
        noMoreTv.setVisibility(VISIBLE);
    }
}