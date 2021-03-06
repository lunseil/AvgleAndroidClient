package pl.avgle.videos.main.view.fragment;

import android.app.ActivityOptions;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ProgressBar;

import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import butterknife.BindView;
import butterknife.ButterKnife;
import pl.avgle.videos.R;
import pl.avgle.videos.adapter.TagsAdapter;
import pl.avgle.videos.bean.ChangeState;
import pl.avgle.videos.bean.EventState;
import pl.avgle.videos.bean.SelectBean;
import pl.avgle.videos.bean.TagsBean;
import pl.avgle.videos.config.QueryType;
import pl.avgle.videos.database.DatabaseUtil;
import pl.avgle.videos.main.base.LazyFragment;
import pl.avgle.videos.main.contract.TagsContract;
import pl.avgle.videos.main.presenter.TagsPresenter;
import pl.avgle.videos.main.view.activity.VideosActivity;
import pl.avgle.videos.util.SharedPreferencesUtils;
import pl.avgle.videos.util.Utils;

public class FavoriteTagsFragment extends LazyFragment<TagsContract.View, TagsPresenter> implements TagsContract.View {
    @BindView(R.id.rv_list)
    RecyclerView mRecyclerView;
    @BindView(R.id.loading)
    ProgressBar loading;
    private TagsAdapter mTagsAdapter;
    private List<TagsBean.ResponseBean.CollectionsBean> list = new ArrayList<>();
    private View view;
    private AlertDialog alertDialog;
    private FloatingActionButton fab;

    int position = 0;
    private GridLayoutManager gridLayoutManager;

    @Override
    protected View initViews(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        if (view == null) {
            view = inflater.inflate(R.layout.fragment_favorite, container, false);
            mUnBinder = ButterKnife.bind(this, view);
        } else {
            ViewGroup parent = (ViewGroup) view.getParent();
            if (parent != null) {
                parent.removeView(view);
            }
        }
        fab = getActivity().findViewById(R.id.fab);
        if (isFragmentVisible) setFabOnClick();
        if (Utils.checkHasNavigationBar(getActivity()))
            mRecyclerView.setPadding(0,0,0, Utils.getNavigationBarHeight(getActivity()));
        initAdapter();
        return view;
    }

    @Override
    public void setUserVisibleHint(boolean isVisibleToUser) {
        super.setUserVisibleHint(isVisibleToUser);
        if (getUserVisibleHint()) {
            if (fab != null) setFabOnClick();
        }
    }

    private void setFabOnClick() {
        fab.setOnClickListener(view -> addTags());
    }

    @Override
    protected TagsPresenter createPresenter() {
        return new TagsPresenter(true, this);
    }

    public void initAdapter() {
        if (mTagsAdapter == null) {
            mTagsAdapter = new TagsAdapter(getActivity(), list);
            mTagsAdapter.setOnItemClickListener((adapter, view, position) -> {
                TagsBean.ResponseBean.CollectionsBean bean = (TagsBean.ResponseBean.CollectionsBean) adapter.getItem(position);
                Intent intent = new Intent(getActivity(), VideosActivity.class);
                Bundle bundle = new Bundle();
                bundle.putSerializable("bean", bean);
                bundle.putInt("type", QueryType.COLLECTIONS_TYPE);
                bundle.putString("name", bean.getTitle());
                bundle.putString("img", bean.getCover_url());
                bundle.putString("order", (String) SharedPreferencesUtils.getParam(getActivity(), "videos_order", "mr"));
                intent.putExtras(bundle);
                if (isPortrait)
                    startActivity(intent, ActivityOptions.makeSceneTransitionAnimation(getActivity(), view, "sharedImg").toBundle());
                else
                    startActivity(intent);
            });
            mTagsAdapter.setOnItemLongClickListener((adapter, view, position) -> {
                mBottomSheetDialogTitle.setText(list.get(position).getTitle());
                selectBeanList = new ArrayList<>();
                selectBeanList.add(new SelectBean(Utils.getString(R.string.remove_favorite), R.drawable.baseline_remove_white_48dp));
                selectAdapter.setNewData(selectBeanList);
                selectAdapter.setOnItemClickListener((selectAdapter, selectView, selectPosition) -> {
                    removeTag(position, list.get(position).getTitle());
                    mBottomSheetDialog.dismiss();
                });
                mBottomSheetDialog.getBehavior().setState(BottomSheetBehavior.STATE_EXPANDED);
                mBottomSheetDialog.show();
                return true;
            });
            mRecyclerView.setHasFixedSize(true);
            mRecyclerView.setAdapter(mTagsAdapter);
        }
    }

    /**
     * 移除收藏
     */
    private void removeTag(int position, String title) {
        DatabaseUtil.deleteTag(title);
        mTagsAdapter.remove(position);
        list.remove(position);
        if (list.size() == 0) {
            mTagsAdapter.setNewData(list);
            showLoadErrorView(Utils.getString(R.string.empty_channel));
        }
    }

    /**
     * 添加标签
     */
    public void addTags() {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity(), R.style.MyDialogTheme);
        View view = LayoutInflater.from(getActivity()).inflate(R.layout.dialog_add_tag, null);
        EditText et = view.findViewById(R.id.tag);
        builder.setPositiveButton(Utils.getString(R.string.favorite_tag_dialog_positive), null);
        builder.setNegativeButton(Utils.getString(R.string.favorite_tag_dialog_negative), null);
        builder.setTitle(Utils.getString(R.string.favorite_tag_dialog_title));
        builder.setCancelable(false);
        alertDialog = builder.setView(view).create();
        alertDialog.show();
        alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String text = et.getText().toString();
            if (!text.trim().isEmpty()) {
                addTag(text);
                alertDialog.dismiss();
            } else {
                et.setError(Utils.getString(R.string.favorite_tag_dialog_error));
                return;
            }
        });
        alertDialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener(v -> alertDialog.dismiss());
    }

    /**
     * 自定义标签
     */
    public void addTag(final String str) {
        if (DatabaseUtil.checkTag(str))
            application.showToastMsg(Utils.getString(R.string.tag_is_exist));
        else {
            TagsBean.ResponseBean.CollectionsBean bean = new TagsBean.ResponseBean.CollectionsBean();
            bean.setId(UUID.randomUUID().toString());
            bean.setTitle(str);
            bean.setKeyword("自定义");
            bean.setCover_url("");
            bean.setTotal_views(0);
            bean.setVideo_count(0);
            bean.setCollection_url("");
            DatabaseUtil.addTags(bean);
            mTagsAdapter.addData(0, bean);
        }
    }

    @Override
    protected void loadData() {
        mPresenter.loadData();
    }

    @Override
    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(EventState eventState) {
        if (eventState.getState() == 1 && list.size() > 0) {
            mTagsAdapter.setNewData(new ArrayList<>());
            loadData();
        }
    }

    @Override
    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onChangeState(ChangeState changeState) {
        if (changeState.isPortrait()) setPortrait();
        else setLandscape();
    }

    protected void setLandscape() {
        if (list.size() > 0) {
            setGridSpaceItemDecoration(mRecyclerView, 4);
            if (gridLayoutManager != null)
                position = ((GridLayoutManager) mRecyclerView.getLayoutManager()).findFirstVisibleItemPosition();
            gridLayoutManager = new GridLayoutManager(getActivity(), 4);
            mRecyclerView.setLayoutManager(gridLayoutManager);
            mRecyclerView.getLayoutManager().scrollToPosition(position);
        }
    }

    protected void setPortrait() {
        if (list.size() > 0) {
            setGridSpaceItemDecoration(mRecyclerView, Utils.isTabletDevice(getActivity()) ? 3 : 2);
            if (gridLayoutManager != null)
                position = ((GridLayoutManager) mRecyclerView.getLayoutManager()).findFirstVisibleItemPosition();
            gridLayoutManager = new GridLayoutManager(getActivity(), Utils.isTabletDevice(getActivity()) ? 3 : 2);
            mRecyclerView.setLayoutManager(gridLayoutManager);
            mRecyclerView.getLayoutManager().scrollToPosition(position);
        }
    }

    @Override
    public void showLoadingView() {}

    @Override
    public void showLoadErrorView(String msg) {
        loading.setVisibility(View.GONE);
        errorTitle.setText(msg);
        mRecyclerView.setLayoutManager(new GridLayoutManager(getActivity(), 1));
        mTagsAdapter.setEmptyView(errorView);
    }

    @Override
    public void showEmptyVIew() {}

    @Override
    public void showLoadSuccessView(TagsBean bean, boolean isLoad) {}

    @Override
    public void showUserFavoriteView(List<TagsBean.ResponseBean.CollectionsBean> list) {
        loading.setVisibility(View.GONE);
        this.list = list;
        setRecyclerView();
        mTagsAdapter.setNewData(this.list);
    }

    private void setRecyclerView() {
        if (isPortrait) setPortrait();
        else setLandscape();
    }
}
