package jp.redmine.redmineclient.fragment;

import java.sql.SQLException;

import jp.redmine.redmineclient.R;
import jp.redmine.redmineclient.activity.handler.IssueActionEmptyHandler;
import jp.redmine.redmineclient.activity.handler.IssueActionInterface;
import jp.redmine.redmineclient.activity.handler.TimeentryActionEmptyHandler;
import jp.redmine.redmineclient.activity.handler.TimeentryActionInterface;
import jp.redmine.redmineclient.activity.handler.WebviewActionEmptyHandler;
import jp.redmine.redmineclient.activity.handler.WebviewActionInterface;
import jp.redmine.redmineclient.adapter.RedmineIssueViewStickyListHeadersAdapter;
import jp.redmine.redmineclient.db.cache.DatabaseCacheHelper;
import jp.redmine.redmineclient.db.cache.RedmineIssueModel;
import jp.redmine.redmineclient.entity.RedmineIssue;
import jp.redmine.redmineclient.entity.RedmineIssueRelation;
import jp.redmine.redmineclient.entity.RedmineTimeEntry;
import jp.redmine.redmineclient.model.ConnectionModel;
import jp.redmine.redmineclient.param.IssueArgument;
import jp.redmine.redmineclient.task.SelectIssueJournalTask;
import android.app.Activity;
import android.os.AsyncTask.Status;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;

import com.j256.ormlite.android.apptools.OrmLiteListFragment;

public class IssueView extends OrmLiteListFragment<DatabaseCacheHelper> {
	private final String TAG = IssueView.class.getSimpleName();
	private RedmineIssueViewStickyListHeadersAdapter adapter;
	private SelectDataTask task;
	private MenuItem menu_refresh;
	private View mFooter;
	private WebviewActionInterface mActionListener;
	private IssueActionInterface mListener;
	private TimeentryActionInterface mTimeEntryListener;

	public IssueView(){
		super();
	}

	static public IssueView newInstance(IssueArgument intent){
		IssueView instance = new IssueView();
		instance.setArguments(intent.getArgument());
		return instance;
	}

	@Override
	public void onDestroyView() {
		cancelTask(true);
		setListAdapter(null);
		super.onDestroyView();
	}
	protected void cancelTask(boolean isForce){
		// cleanup task
		if(task != null && task.getStatus() == Status.RUNNING){
			task.cancel(isForce);
		}
	}
	@Override
	public void onPause() {
		cancelTask(false);
		super.onPause();
	}

	@Override
	public void onAttach(Activity activity) {
		super.onAttach(activity);
		if(activity instanceof ActivityInterface){
			ActivityInterface aif = (ActivityInterface)activity;
			mActionListener = aif.getHandler(WebviewActionInterface.class);
			mListener = aif.getHandler(IssueActionInterface.class);
			mTimeEntryListener = aif.getHandler(TimeentryActionInterface.class);
		}
		if(mActionListener == null) {
			//setup empty events
			mActionListener = new WebviewActionEmptyHandler();
		}
		if(mListener == null){
			//setup empty events
			mListener = new IssueActionEmptyHandler();
		}
		if(mTimeEntryListener == null){
			//setup empty events
			mTimeEntryListener = new TimeentryActionEmptyHandler();
		}

	}
	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);

		getListView().addFooterView(mFooter);

		adapter = new RedmineIssueViewStickyListHeadersAdapter(getHelper(),mActionListener);
		
		setListAdapter(adapter);
		
		getListView().setFastScrollEnabled(true);

		onRefresh(true);


	}
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setHasOptionsMenu(true);
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		mFooter = inflater.inflate(R.layout.listview_footer,null);
		mFooter.setVisibility(View.GONE);
		return inflater.inflate(R.layout.stickylistheaderslist, container, false);
	}

	@Override
	public void onListItemClick(ListView l, View v, int position, long id) {
		Object item = adapter.getItem(position);
		if(item == null){}
		else if (item instanceof RedmineTimeEntry){
			RedmineTimeEntry entry = (RedmineTimeEntry)item;
			mTimeEntryListener.onTimeEntrySelected(entry.getConnectionId(), entry.getIssueId(), entry.getTimeentryId());
			return;
		} else if (item instanceof RedmineIssueRelation){
			RedmineIssueRelation relation = (RedmineIssueRelation)item;
			if(relation.getIssue() != null){
				mListener.onIssueSelected(relation.getConnectionId(), relation.getIssue().getIssueId());
				return;
			}
		} else if (item instanceof RedmineIssue) {
			RedmineIssue issue = (RedmineIssue)item;
			if(v.getId() == R.id.textProject && issue.getProject() != null){
				mListener.onIssueList(issue.getConnectionId(), issue.getProject().getId());
				return;
			}
		}
		super.onListItemClick(l, v, position, id);
	}
	
	protected void onRefresh(boolean isFetch){
		IssueArgument intent = new IssueArgument();
		intent.setArgument(getArguments());
		RedmineIssueModel mIssue = new RedmineIssueModel(getHelper());
		Long issue_id = null;
		try {
			issue_id = mIssue.getIdByIssue(intent.getConnectionId(),intent.getIssueId());
		} catch (SQLException e) {
			Log.e(TAG,"onRefresh",e);
		}
		if(issue_id != null){
			adapter.setupParameter(intent.getConnectionId(),issue_id);
			adapter.notifyDataSetInvalidated();
			adapter.notifyDataSetChanged();
		}

		if(adapter.getJournalCount() < 1 && isFetch){
			onFetchRemote();
		}
	}

	protected void onFetchRemote(){
		if(task != null && task.getStatus() == Status.RUNNING)
			return;
		IssueArgument intent = new IssueArgument();
		intent.setArgument(getArguments());
		task = new SelectDataTask();
		task.execute(intent.getIssueId());
	}

	private class SelectDataTask extends SelectIssueJournalTask{
		public SelectDataTask() {
			super();
			helper = getHelper();
			IssueArgument intent = new IssueArgument();
			intent.setArgument(getArguments());
			int connectionid = intent.getConnectionId();
			ConnectionModel mConnection = new ConnectionModel(getActivity());
			connection = mConnection.getItem(connectionid);
			mConnection.finalize();
		}
		// can use UI thread here
		@Override
		protected void onPreExecute() {
			mFooter.setVisibility(View.VISIBLE);
			if(menu_refresh != null)
				menu_refresh.setEnabled(false);
		}

		// can use UI thread here
		@Override
		protected void onPostExecute(Void v) {
			onRefresh(false);
			onStopped();

			IssueArgument intent = new IssueArgument();
			intent.setArgument(getArguments());
			mListener.onIssueRefreshed(intent.getConnectionId(), intent.getIssueId());
		}
		@Override
		protected void onCancelled() {
			super.onCancelled();
			onStopped();
		}

		protected void onStopped(){
			mFooter.setVisibility(View.GONE);
			if(menu_refresh != null)
				menu_refresh.setEnabled(true);
		}
	}
	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater)
	{
		super.onCreateOptionsMenu(menu, inflater);
		inflater.inflate( R.menu.issue_view, menu );
		menu_refresh = menu.findItem(R.id.menu_refresh);
		if(task != null && task.getStatus() == Status.RUNNING)
			menu_refresh.setEnabled(false);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item)
	{
		switch ( item.getItemId() )
		{
			case R.id.menu_refresh:
			{
				onFetchRemote();
				return true;
			}
			case R.id.menu_edit:
			{
				IssueArgument baseintent = new IssueArgument();
				baseintent.setArgument(getArguments());
				mListener.onIssueEdit(baseintent.getConnectionId(), baseintent.getIssueId());
				return true;
			}
			case R.id.menu_add_timeentry:
			{
				IssueArgument baseintent = new IssueArgument();
				baseintent.setArgument(getArguments());
				mTimeEntryListener.onTimeEntryAdd(baseintent.getConnectionId(), baseintent.getIssueId());
				return true;
			}
		}
		return super.onOptionsItemSelected(item);
	}

}
