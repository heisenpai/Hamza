/**
 * 
 */
package net.osmand.plus.activities.search;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.osmand.CollatorStringMatcher;
import net.osmand.CollatorStringMatcher.StringMatcherMode;
import net.osmand.access.AccessibleToast;
import net.osmand.data.LatLon;
import net.osmand.osm.AbstractPoiType;
import net.osmand.osm.PoiType;
import net.osmand.plus.IconsCache;
import net.osmand.plus.OsmandApplication;
import net.osmand.plus.R;
import net.osmand.plus.activities.EditPOIFilterActivity;
import net.osmand.plus.activities.search.SearchActivity.SearchActivityChild;
import net.osmand.plus.poi.NameFinderPoiFilter;
import net.osmand.plus.poi.PoiFiltersHelper;
import net.osmand.plus.poi.PoiLegacyFilter;
import net.osmand.plus.poi.SearchByNameFilter;
import net.osmand.plus.render.RenderingIcons;
import net.osmand.plus.resources.ResourceManager;
import net.osmand.util.Algorithms;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.ListFragment;
import android.support.v7.widget.PopupMenu;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;



public class SearchPoiFilterFragment extends ListFragment implements SearchActivityChild {

	public static final String SEARCH_LAT = SearchActivity.SEARCH_LAT;
	public static final String SEARCH_LON = SearchActivity.SEARCH_LON;
	public static final int REQUEST_POI_EDIT = 55;

	private EditText searchEditText;
	private SearchPoiByNameTask currentTask = null;
	private PoiFiltersAdapter poiFitlersAdapter;
	
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.searchpoi, container, false);
        
        v.findViewById(R.id.SearchFilterLayout).setVisibility(View.VISIBLE);
        setupSearchEditText((EditText) v.findViewById(R.id.edit));
        setupOptions(v.findViewById(R.id.options));
        v.findViewById(R.id.poiSplitbar).setVisibility(View.GONE);
        return v;
    }
	
	private void setupOptions(View options) {
		options.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				showOptionsMenu(v);
			}
		});
	}

	private void setupSearchEditText(EditText e) {
		searchEditText = e;
		searchEditText.addTextChangedListener(new TextWatcher() {
			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) {
			}

			@Override
			public void beforeTextChanged(CharSequence s, int start, int count, int after) {
			}

			@Override
			public void afterTextChanged(Editable s) {
				if(currentTask != null) {
					currentTask.cancel(true);
				}
				currentTask = new SearchPoiByNameTask();
				currentTask.execute(s.toString());
			}
		});
	}
	
	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
		poiFitlersAdapter = new PoiFiltersAdapter(getFilters(""));
		setListAdapter(poiFitlersAdapter);
		setHasOptionsMenu(true);
	}

	public List<Object> getFilters(String s) {
		List<Object> filters = new ArrayList<Object>() ;
		if (Algorithms.isEmpty(s)) {
			PoiFiltersHelper poiFilters = getApp().getPoiFilters();
			filters.addAll(poiFilters.getTopDefinedPoiFilters());
		} else {
			PoiFiltersHelper poiFilters = getApp().getPoiFilters();
			for(PoiLegacyFilter pf : poiFilters.getTopDefinedPoiFilters()) {
				if(!pf.isStandardFilter()) {
					filters.add(pf);
				}
			}
			Map<String, AbstractPoiType> res = 
					getApp().getPoiTypes().getAllTypesTranslatedNames(new CollatorStringMatcher(s, StringMatcherMode.CHECK_STARTS_FROM_SPACE));
			for(AbstractPoiType p : res.values()) {
				filters.add(p);
			}
		}
		return filters;
	}
	
	public OsmandApplication getApp(){
		return (OsmandApplication) getActivity().getApplication();
	}
	
	
	private void updateIntentToLaunch(Intent intentToLaunch){
		LatLon loc = null;
		boolean searchAround = false;
		FragmentActivity parent = getActivity();
		if (loc == null && parent instanceof SearchActivity) {
			loc = ((SearchActivity) parent).getSearchPoint();
			searchAround = ((SearchActivity) parent).isSearchAroundCurrentLocation();
		}
		if (loc == null && !searchAround) {
			loc = getApp().getSettings().getLastKnownMapLocation();
		}
		if(loc != null && !searchAround) {
			intentToLaunch.putExtra(SearchActivity.SEARCH_LAT, loc.getLatitude());
			intentToLaunch.putExtra(SearchActivity.SEARCH_LON, loc.getLongitude());
		}
	}

	private void showEditActivity(PoiLegacyFilter poi) {
		Intent newIntent = new Intent(getActivity(), EditPOIFilterActivity.class);
		// folder selected
		newIntent.putExtra(EditPOIFilterActivity.AMENITY_FILTER, poi.getFilterId());
		updateIntentToLaunch(newIntent);
		startActivityForResult(newIntent, REQUEST_POI_EDIT);
	}
	
	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		if(requestCode == REQUEST_POI_EDIT) {
			poiFitlersAdapter.setResult(getFilters(searchEditText == null ? "" : searchEditText.getText().toString()));
		}
	}

	@Override
	public void onListItemClick(ListView listView, View v, int position, long id) {
		final Object item = ((PoiFiltersAdapter) getListAdapter()).getItem(position);
		ResourceManager rm = getApp().getResourceManager();
		if (!rm.containsAmenityRepositoryToSearch(false)) {
			AccessibleToast.makeText(getActivity(), R.string.data_to_search_poi_not_available, Toast.LENGTH_LONG);
			return;
		}
		if (item instanceof PoiLegacyFilter) {
			showFilterActivity(((PoiLegacyFilter) item).getFilterId());
		} else {
			showFilterActivity(PoiLegacyFilter.STD_PREFIX +  ((AbstractPoiType) item).getKeyName());
		}
	}

	private void showFilterActivity(String filterId) {
		final Intent newIntent = new Intent(getActivity(), SearchPOIActivity.class);
		newIntent.putExtra(SearchPOIActivity.AMENITY_FILTER, filterId);
		updateIntentToLaunch(newIntent);
		startActivityForResult(newIntent, 0);
	}

	class SearchPoiByNameTask extends AsyncTask<String, Object, List<Object>> {

		@Override
		protected List<Object> doInBackground(String... params) {
			String filter = params[0];
			return getFilters(filter);
		}
		
		@Override
		protected void onPostExecute(List<Object> result) {
			if(!isCancelled() && isVisible()){
				poiFitlersAdapter.setResult(result);
			}
		}
		
	}


	class PoiFiltersAdapter extends ArrayAdapter<Object> {
		
		PoiFiltersAdapter(List<Object> list) {
			super(getActivity(), R.layout.searchpoifolder_list, list);
		}

		public void setResult(List<Object> filters) {
			setNotifyOnChange(false);
			clear();
			for(Object o : filters) {
				add(o);
			}
			setNotifyOnChange(true);
			notifyDataSetInvalidated();
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			View row = convertView;
			if (row == null) {
				LayoutInflater inflater = getActivity().getLayoutInflater();
				row = inflater.inflate(R.layout.searchpoifolder_list, parent, false);
			}
			TextView label = (TextView) row.findViewById(R.id.folder_label);
			ImageView icon = (ImageView) row.findViewById(R.id.folder_icon);
			Object item = getItem(position);
			String name;
			if (item instanceof PoiLegacyFilter) {
				final PoiLegacyFilter model = (PoiLegacyFilter) item;
				if (RenderingIcons.containsBigIcon(model.getSimplifiedId())) {
					icon.setImageDrawable(RenderingIcons.getBigIcon(getActivity(), model.getSimplifiedId()));
				} else {
					icon.setImageResource(R.drawable.mx_user_defined);
				}
				name = model.getName();
			} else {
				AbstractPoiType st = (AbstractPoiType) item;
				if (RenderingIcons.containsBigIcon(st.getKeyName())) {
					icon.setImageDrawable(RenderingIcons.getBigIcon(getActivity(), st.getKeyName()));
				} else if (st instanceof PoiType
						&& RenderingIcons.containsBigIcon(((PoiType) st).getOsmTag() + "_"
								+ ((PoiType) st).getOsmValue())) {
					icon.setImageResource(RenderingIcons.getBigIconResourceId(((PoiType) st).getOsmTag() + "_"
							+ ((PoiType) st).getOsmValue()));
				} else {
					icon.setImageDrawable(null);
				}
				name = st.getTranslation();
			}
			label.setText(name);
			return (row);
		}
	}
	
	private void showOptionsMenu(View v) {
		// Show menu with search all, name finder, name finder poi
		IconsCache iconsCache = getMyApplication().getIconsCache();
		final PopupMenu optionsMenu = new PopupMenu(getActivity(), v);

		MenuItem item = optionsMenu.getMenu().add(R.string.poi_filter_custom_filter)
				.setIcon(iconsCache.getContentIcon(R.drawable.ic_action_filter_dark));
		item.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
			@Override
			public boolean onMenuItemClick(MenuItem item) {
				PoiLegacyFilter filter = getApp().getPoiFilters().getCustomPOIFilter();
				filter.clearFilter();
				showEditActivity(filter);
				return true;
			}
		});
		optionsMenu.show();

	}

	@Override
	public void onCreateOptionsMenu(Menu onCreate, MenuInflater inflater) {
		if(getActivity() instanceof SearchActivity) {
			((SearchActivity) getActivity()).getClearToolbar(false);
		}
	}

	@Override
	public void locationUpdate(LatLon l) {
	}

	public OsmandApplication getMyApplication() {
		return (OsmandApplication) getActivity().getApplication();
	}

}
