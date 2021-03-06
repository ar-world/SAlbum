package com.sharry.lib.album;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.app.Fragment;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.snackbar.Snackbar;
import com.sharry.lib.album.photoview.PhotoView;
import com.sharry.lib.album.toolbar.SToolbar;
import com.sharry.lib.album.toolbar.ViewOptions;

import java.util.ArrayList;

import static com.sharry.lib.album.ActivityStateUtil.fixRequestOrientation;

/**
 * 图片查看器的 Activity, 主题设置为背景透明效果更佳
 *
 * @author Sharry <a href="SharryChooCHN@Gmail.com">Contact me.</a>
 * @version 1.3
 * @since 2018/9/22 23:24
 */
public class WatcherActivity extends AppCompatActivity implements
        WatcherContract.IView,
        DraggableViewPager.Callback,
        PickedPanelAdapter.Interaction {

    private static final String EXTRA_SHARED_ELEMENT = "start_intent_extra_shared_element";
    static final int REQUEST_CODE = 508;
    static final String RESULT_EXTRA_IS_PICKED_ENSURE = "result_extra_is_picked_ensure";
    static final String RESULT_EXTRA_PICKED_SET = "result_extra_picked_set";


    static final String BROADCAST_PICKED_SET_CHANGED = "com.sharry.lib.album.watcheractivity.broadcast.picked.set.changed";
    static final String BROADCAST_EXTRA_DATA = "BROADCAST_EXTRA_DATA";


    /**
     * 图片浏览器的配置, 使用静态变量暂存数据
     * <p>
     * 防止图片资源超过 1MB - 8k, 导致 Binder 驱动无法传值
     */
    private static WatcherConfig sConfig;

    /**
     * U can launch this activity from here.
     *
     * @param request       请求的 Activity
     * @param resultTo      WatcherActivity 返回值的去向
     * @param config        WatcherActivity 的配置
     * @param sharedElement 共享元素
     */
    static void launchActivityForResult(@NonNull Activity request, @NonNull Fragment resultTo,
                                        @NonNull WatcherConfig config, @Nullable View sharedElement) {
        // 暂存 Config
        sConfig = config;
        Intent intent = new Intent(request, WatcherActivity.class);
        if (sharedElement != null) {
            intent.putExtra(
                    WatcherActivity.EXTRA_SHARED_ELEMENT,
                    SharedElementHelper.Bounds.parseFrom(sharedElement, config.getPosition())
            );
        }
        resultTo.startActivityForResult(intent, REQUEST_CODE);
        if (sharedElement != null) {
            request.overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        }
    }

    /**
     * The presenter for the view.
     */
    private WatcherContract.IPresenter mPresenter;

    /**
     * Widgets for this Activity.
     */
    private TextView mTvTitle;
    private PhotoView mIvPlaceHolder;
    private CheckedIndicatorView mCheckIndicator;
    private DraggableViewPager mDisplayPager;
    private DisplayAdapter mDisplayAdapter;
    private LinearLayout mLlPickedPanelContainer;
    private RecyclerView mRvPickedPanel;
    private TextView mTvEnsure;

    /**
     * The animator for bottom preview.
     */
    private ObjectAnimator mBottomPreviewShowAnimator;
    private ObjectAnimator mBottomPreviewDismissAnimator;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        fixRequestOrientation(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.lib_album_activity_watcher);
        initTitle();
        initViews();
        initPresenter();
    }


    ////////////////////////////////////////// Internal methods /////////////////////////////////////////////

    private void initTitle() {
        SToolbar toolbar = findViewById(R.id.toolbar);
        mTvTitle = toolbar.getTitleText();
        // 添加右部的索引
        mCheckIndicator = new CheckedIndicatorView(this);
        toolbar.addRightMenuView(mCheckIndicator, new ViewOptions.Builder()
                .setVisibility(View.INVISIBLE)
                .setWidthExcludePadding(DensityUtil.dp2px(this, 25))
                .setHeightExcludePadding(DensityUtil.dp2px(this, 25))
                .setPaddingRight(DensityUtil.dp2px(this, 10))
                .setListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        mPresenter.handleIndicatorClick(mCheckIndicator.isChecked());
                    }
                })
                .build());
    }

    private void initViews() {
        // 占位图
        mIvPlaceHolder = findViewById(R.id.iv_se_place_holder);
        // 1. 初始化 ViewPager
        mDisplayPager = findViewById(R.id.view_pager);
        mDisplayPager.setCallback(this);
        mDisplayPager.setBackgroundColorRes(R.color.lib_album_watcher_bg_color);
        // 2. 初始化底部菜单
        mLlPickedPanelContainer = findViewById(R.id.ll_picked_panel_container);
        mRvPickedPanel = findViewById(R.id.rv_picked_panel);
        mRvPickedPanel.setLayoutManager(new LinearLayoutManager(this,
                LinearLayoutManager.HORIZONTAL, false));
        mTvEnsure = findViewById(R.id.tv_ensure);
        mTvEnsure.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mPresenter.handleEnsureClicked();
            }
        });
    }

    private void initPresenter() {
        mPresenter = new WatcherPresenter(
                this,
                sConfig,
                ((SharedElementHelper.Bounds) getIntent().getParcelableExtra(EXTRA_SHARED_ELEMENT))
        );
    }

    @Override
    public void onBackPressed() {
        mPresenter.handleBackPressed();
    }

    @Override
    public void finish() {
        mPresenter.handleBeforeFinish();
        super.finish();
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        sConfig = null;
        WatcherFragment.ACTIVES.clear();
        WatcherFragment.IDLES.clear();
    }

    //////////////////////////////////////////////WatcherContract.IView/////////////////////////////////////////////////

    @Override
    public void showSharedElementEnter(@NonNull MediaMeta mediaMeta, @NonNull final SharedElementHelper.Bounds data) {
        // 加载共享元素占位图
        mIvPlaceHolder.setVisibility(View.VISIBLE);
        Loader.loadPicture(this, mediaMeta, mIvPlaceHolder);
        // 执行共享元素
        mIvPlaceHolder.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                mIvPlaceHolder.getViewTreeObserver().removeOnPreDrawListener(this);
                // Execute enter animator.
                Animator startAnim = SharedElementHelper.createSharedElementEnterAnimator(mIvPlaceHolder, data);
                startAnim.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        mIvPlaceHolder.setVisibility(View.GONE);
                    }
                });
                startAnim.start();
                return true;
            }
        });
    }

    @Override
    public void showSharedElementExitAndFinish(@NonNull SharedElementHelper.Bounds data) {
        final WatcherFragment watcherFragment = mDisplayAdapter.getItem(data.position);
        final PhotoView target = watcherFragment.getPhotoView();
        Animator exitAnim = SharedElementHelper.createSharedElementExitAnimator(target, data);
        if (exitAnim == null) {
            this.finish();
            return;
        }
        exitAnim.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                watcherFragment.dismissOtherView();
                mDisplayPager.setBackgroundColor(Color.TRANSPARENT);
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                WatcherActivity.this.finish();
            }
        });
        exitAnim.start();
    }

    @Override
    public void setLeftTitleText(@NonNull CharSequence content) {
        mTvTitle.setText(content);
    }

    @Override
    public void setIndicatorText(@NonNull CharSequence indicatorText) {
        mCheckIndicator.setText(indicatorText);
    }

    @Override
    public void setIndicatorColors(int indicatorBorderCheckedColor, int indicatorBorderUncheckedColor,
                                   int indicatorSolidColor, int indicatorTextColor) {
        mCheckIndicator.setBorderColor(indicatorBorderCheckedColor, indicatorBorderUncheckedColor);
        mCheckIndicator.setSolidColor(indicatorSolidColor);
        mCheckIndicator.setTextColor(indicatorTextColor);
    }

    @Override
    public void setIndicatorVisible(boolean isShowCheckedIndicator) {
        mCheckIndicator.setVisibility(isShowCheckedIndicator ? View.VISIBLE : View.GONE);
    }

    @Override
    public void setIndicatorChecked(boolean isChecked) {
        mCheckIndicator.setChecked(isChecked);
    }

    @Override
    public void setEnsureText(@NonNull CharSequence content) {
        mTvEnsure.setText(content);
    }

    @Override
    public void setDisplayAdapter(@NonNull ArrayList<MediaMeta> items) {
        mDisplayAdapter = new DisplayAdapter(getSupportFragmentManager(), items);
        mDisplayPager.setAdapter(mDisplayAdapter);
    }

    @Override
    public void displayAt(int position) {
        mDisplayPager.setCurrentItem(position);
    }

    @Override
    public void setPickedAdapter(@NonNull ArrayList<MediaMeta> pickedSet) {
        mRvPickedPanel.setAdapter(new PickedPanelAdapter(pickedSet, this));
    }

    @Override
    public void notifyItemRemoved(@NonNull MediaMeta removedMeta, int removedIndex) {
        RecyclerView.Adapter adapter;
        if ((adapter = mRvPickedPanel.getAdapter()) != null) {
            adapter.notifyItemRemoved(removedIndex);
        }
        Intent data = new Intent(BROADCAST_PICKED_SET_CHANGED);
        data.putExtra(BROADCAST_EXTRA_DATA, removedMeta);
        LocalBroadcastManager.getInstance(this).sendBroadcast(data);
    }

    @Override
    public void notifyItemPicked(@NonNull MediaMeta addedMeta, int addedIndex) {
        RecyclerView.Adapter adapter;
        if ((adapter = mRvPickedPanel.getAdapter()) != null) {
            adapter.notifyItemInserted(addedIndex);
        }
        Intent data = new Intent(BROADCAST_PICKED_SET_CHANGED);
        data.putExtra(BROADCAST_EXTRA_DATA, addedMeta);
        LocalBroadcastManager.getInstance(this).sendBroadcast(data);
    }

    @Override
    public void showPickedPanel() {
        if (mLlPickedPanelContainer.getVisibility() == View.VISIBLE) {
            return;
        }
        if (mBottomPreviewShowAnimator == null) {
            mBottomPreviewShowAnimator = ObjectAnimator.ofFloat(mLlPickedPanelContainer,
                    "translationY", mLlPickedPanelContainer.getHeight(), 0);
            mBottomPreviewShowAnimator.setDuration(200);
            mBottomPreviewShowAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationStart(Animator animation) {
                    mLlPickedPanelContainer.setVisibility(View.VISIBLE);
                }
            });
        }
        mBottomPreviewShowAnimator.start();
    }

    @Override
    public void dismissPickedPanel() {
        if (mLlPickedPanelContainer.getVisibility() == View.INVISIBLE) {
            return;
        }
        if (mBottomPreviewDismissAnimator == null) {
            mBottomPreviewDismissAnimator = ObjectAnimator.ofFloat(mLlPickedPanelContainer,
                    "translationY", 0, mLlPickedPanelContainer.getHeight());
            mBottomPreviewDismissAnimator.setDuration(200);
            mBottomPreviewDismissAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    mLlPickedPanelContainer.setVisibility(View.INVISIBLE);
                }
            });
        }
        mBottomPreviewDismissAnimator.start();
    }

    @Override
    public void pickedPanelSmoothScrollToPosition(int position) {
        mRvPickedPanel.smoothScrollToPosition(position);
    }

    @Override
    public void showMsg(@NonNull String msg) {
        Snackbar.make(mRvPickedPanel, msg, Snackbar.LENGTH_LONG).show();
    }

    @Override
    public void setResult(boolean isEnsurePressed, ArrayList<MediaMeta> pickedSet) {
        Intent intent = new Intent();
        intent.putExtra(RESULT_EXTRA_IS_PICKED_ENSURE, isEnsurePressed);
        intent.putExtra(RESULT_EXTRA_PICKED_SET, pickedSet);
        setResult(RESULT_OK, intent);
    }

    ////////////////////////////////////////// DraggableViewPager.Callback /////////////////////////////////////////////

    @Override
    public void onPagerChanged(int position) {
        if (mPresenter != null) {
            mPresenter.handlePagerChanged(position);
        }
    }

    @Override
    public boolean handleDismissAction() {
        return mPresenter.handleDisplayPagerDismiss();
    }

    @Override
    public void onDismissed() {
        finish();
    }

    ////////////////////////////////////////// PickedAdapter.Interaction /////////////////////////////////////////////

    @Override
    public void onPreviewItemClicked(ImageView imageView, MediaMeta meta, int position) {
        mPresenter.handlePickedItemClicked(meta);
    }


}