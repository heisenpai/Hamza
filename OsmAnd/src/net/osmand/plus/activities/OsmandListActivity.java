package net.osmand.plus.activities;

import android.support.v4.view.MenuItemCompat;
import android.support.v7.app.ActionBarActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.AdapterView;
import android.widget.HeaderViewListAdapter;
import android.widget.ListAdapter;
import android.widget.ListView;
import net.osmand.plus.OsmandApplication;
import android.app.ActionBar;
import android.graphics.Shader.TileMode;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.View;


public abstract class OsmandListActivity extends
		ActionBarProgressActivity implements AdapterView.OnItemClickListener {

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		((OsmandApplication) getApplication()).applyTheme(this);
		super.onCreate(savedInstanceState);
		getSupportActionBar().setNavigationMode(ActionBar.NAVIGATION_MODE_STANDARD);
	}


	public OsmandApplication getMyApplication() {
		return (OsmandApplication)getApplication();
	}


	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		int itemId = item.getItemId();
		switch (itemId) {
			case android.R.id.home:
				finish();
				return true;

		}
		return false;
	}

	public MenuItem createMenuItem(Menu m, int id, int titleRes, int iconDark, int menuItemType) {
		MenuItem menuItem = m.add(0, id, 0, titleRes);
		if (iconDark != 0) {
			menuItem.setIcon(getMyApplication().getIconsCache().getActionBarIcon(iconDark));
		}
		menuItem.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
			@Override
			public boolean onMenuItemClick(MenuItem item) {
				return onOptionsItemSelected(item);
			}
		});
		MenuItemCompat.setShowAsAction(menuItem, menuItemType);
		return menuItem;
	}

	public void setListAdapter(ListAdapter adapter){
		((ListView)findViewById(android.R.id.list)).setAdapter(adapter);
		setOnItemClickListener(this);

	}

	public ListView getListView() {
		return (ListView)findViewById(android.R.id.list);
	}

	public ListAdapter getListAdapter() {
		ListAdapter adapter = getListView().getAdapter();
		if (adapter instanceof HeaderViewListAdapter) {
			return ((HeaderViewListAdapter)adapter).getWrappedAdapter();
		} else {
			return adapter;
		}
	}

	public void setOnItemClickListener(AdapterView.OnItemClickListener childClickListener){
		((ListView)findViewById(android.R.id.list)).setOnItemClickListener(childClickListener);
	}

	public boolean isLightActionBar() {
		return ((OsmandApplication) getApplication()).getSettings().isLightActionBar();
	}

	@Override
	public void onItemClick(AdapterView<?> parent, View view, int position, long id) {

	}
}
