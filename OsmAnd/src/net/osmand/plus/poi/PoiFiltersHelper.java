package net.osmand.plus.poi;


import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import net.osmand.osm.MapPoiTypes;
import net.osmand.osm.PoiCategory;
import net.osmand.osm.PoiFilter;
import net.osmand.osm.PoiType;
import net.osmand.plus.OsmandApplication;
import net.osmand.plus.R;
import net.osmand.plus.api.SQLiteAPI.SQLiteConnection;
import net.osmand.plus.api.SQLiteAPI.SQLiteCursor;
import net.osmand.plus.api.SQLiteAPI.SQLiteStatement;

public class PoiFiltersHelper {
	private final OsmandApplication application;
	
	private NameFinderPoiFilter nameFinderPOIFilter;
	private PoiLegacyFilter searchByNamePOIFilter;
	private PoiLegacyFilter customPOIFilter;
	private PoiLegacyFilter showAllPOIFilter;
	private List<PoiLegacyFilter> cacheTopStandardFilters;
	
	private static final String UDF_CAR_AID = "car_aid";
	private static final String UDF_FOR_TOURISTS = "for_tourists";
	private static final String UDF_FOOD_SHOP = "food_shop";
	private static final String UDF_FUEL = "fuel";
	private static final String UDF_SIGHTSEEING = "sightseeing";
	private static final String UDF_EMERGENCY = "emergency";
	private static final String UDF_PUBLIC_TRANSPORT = "public_transport";
	private static final String UDF_ACCOMMODATION = "accomodation";
	private static final String UDF_RESTAURANTS = "restaurants";
	private static final String UDF_PARKING = "parking";
	
	private static final String[] DEL = new String[] {
		UDF_CAR_AID, UDF_FOR_TOURISTS, UDF_FOOD_SHOP, UDF_FUEL, UDF_SIGHTSEEING, UDF_EMERGENCY,
		UDF_PUBLIC_TRANSPORT, UDF_ACCOMMODATION, UDF_RESTAURANTS, UDF_PARKING
	};
	
	public PoiFiltersHelper(OsmandApplication application){
		this.application = application;
	}
	
	public NameFinderPoiFilter getNameFinderPOIFilter() {
		if(nameFinderPOIFilter == null){
			nameFinderPOIFilter = new NameFinderPoiFilter(application);
		}
		return nameFinderPOIFilter;
	}
	
	public PoiLegacyFilter getSearchByNamePOIFilter() {
		if(searchByNamePOIFilter == null){
			PoiLegacyFilter filter = new SearchByNameFilter(application);
			filter.setStandardFilter(true);
			searchByNamePOIFilter = filter;
		}
		return searchByNamePOIFilter;
	}
	
	public PoiLegacyFilter getCustomPOIFilter() {
		if(customPOIFilter == null){
			PoiLegacyFilter filter = new PoiLegacyFilter(application.getString(R.string.poi_filter_custom_filter),
					PoiLegacyFilter.CUSTOM_FILTER_ID, new LinkedHashMap<PoiCategory, LinkedHashSet<String>>(), application); //$NON-NLS-1$
			filter.setStandardFilter(true);
			customPOIFilter = filter;
		}
		return customPOIFilter;
	}
	
	public PoiLegacyFilter getShowAllPOIFilter() {
		if(showAllPOIFilter == null){
			PoiLegacyFilter filter = new PoiLegacyFilter(null, application); //$NON-NLS-1$
			filter.setStandardFilter(true);
			showAllPOIFilter = filter;
		}
		return showAllPOIFilter;
	}
	
	
	private PoiLegacyFilter getFilterById(String filterId, PoiLegacyFilter... filters){
		for(PoiLegacyFilter pf : filters) {
			if(pf.getFilterId().equals(filterId)){
				return pf;
			}
		}
		return null;
	}
	
	public PoiLegacyFilter getFilterById(String filterId){
		if(filterId == null){
			return null;
		}
		for(PoiLegacyFilter f : getTopDefinedPoiFilters()) {
			if(f.getFilterId().equals(filterId)){
				return f;
			}
		}
		PoiLegacyFilter ff = getFilterById(filterId, getCustomPOIFilter(), getSearchByNamePOIFilter(),
				getShowAllPOIFilter(), getNameFinderPOIFilter());
		if (ff != null) {
			return ff;
		}
		if(filterId.startsWith(PoiLegacyFilter.STD_PREFIX)) {
			String typeId = filterId.substring(PoiLegacyFilter.STD_PREFIX.length());
			PoiType tp = application.getPoiTypes().getPoiTypeByKey(typeId);
			if(tp != null) {
				return new PoiLegacyFilter(tp, application);
			}
		}
		return null;
	}
	
	
	public void reloadAllPoiFilters() {
		cacheTopStandardFilters = null;
		getTopDefinedPoiFilters();
	}
	
	
	private List<PoiLegacyFilter> getUserDefinedPoiFilters() {
		ArrayList<PoiLegacyFilter> userDefinedFilters = new ArrayList<PoiLegacyFilter>();
		PoiFilterDbHelper helper = openDbHelper();
		if (helper != null) {
			List<PoiLegacyFilter> userDefined = helper.getFilters(helper.getReadableDatabase());
			userDefinedFilters.addAll(userDefined);
			helper.close();
		}
		return userDefinedFilters;
	}
	
	public void sortListOfFilters(List<PoiLegacyFilter> list) {
		final Collator instance = Collator.getInstance();
		Collections.sort(list, new Comparator<PoiLegacyFilter>() {
			private int getRank(PoiLegacyFilter lf) {
				if(PoiLegacyFilter.BY_NAME_FILTER_ID.equals(lf.getFilterId())) {
					return 0;
				} else if(lf.areAllTypesAccepted()) {
					return 3;
				} else if(PoiLegacyFilter.CUSTOM_FILTER_ID.equals(lf.getFilterId())) {
					return 4;
				} else if(PoiLegacyFilter.NAME_FINDER_FILTER_ID.equals(lf.getFilterId())) {
					return 5;
				} else if(lf.isStandardFilter()) {
					return 2;
				}
				return 1;
			}

			@Override
			public int compare(PoiLegacyFilter lhs, PoiLegacyFilter rhs) {
				int lr = getRank(lhs);
				int rr = getRank(rhs);
				if(lr != rr) {
					return lr < rr ? -1 : 1;
				}
				return instance.compare(lhs.getName(), rhs.getName());
			}
		});
		
	}
	
	public List<PoiLegacyFilter> getTopDefinedPoiFilters() {
		if (cacheTopStandardFilters == null) {
			cacheTopStandardFilters = new ArrayList<PoiLegacyFilter>();
			// user defined
			cacheTopStandardFilters.addAll(getUserDefinedPoiFilters());
			// default
			MapPoiTypes poiTypes = application.getPoiTypes();
			for (PoiFilter t : poiTypes.getTopVisibleFilters()) {
				cacheTopStandardFilters.add(new PoiLegacyFilter(t, application));
			}
			sortListOfFilters(cacheTopStandardFilters);
		}
		List<PoiLegacyFilter> result = new ArrayList<PoiLegacyFilter>();
		result.add(getShowAllPOIFilter());
		result.addAll(cacheTopStandardFilters);
		return result;
	}
	
	private PoiFilterDbHelper openDbHelper(){
		if(!application.getPoiTypes().isInit()) {
			return null;
		}
		return new PoiFilterDbHelper(application.getPoiTypes(), application); 
	}
	
	public boolean removePoiFilter(PoiLegacyFilter filter){
		if(filter.getFilterId().equals(PoiLegacyFilter.CUSTOM_FILTER_ID) || 
				filter.getFilterId().equals(PoiLegacyFilter.BY_NAME_FILTER_ID) ||
				filter.getFilterId().startsWith(PoiLegacyFilter.STD_PREFIX)){
			return false;
		}
		PoiFilterDbHelper helper = openDbHelper();
		if(helper == null){
			return false;
		}
		boolean res = helper.deleteFilter(helper.getWritableDatabase(), filter);
		if(res){
			cacheTopStandardFilters.remove(filter);
		}
		helper.close();
		return res;
	}
	
	public boolean createPoiFilter(PoiLegacyFilter filter){
		PoiFilterDbHelper helper = openDbHelper();
		if(helper == null){
			return false;
		}
		boolean res = helper.addFilter(filter, helper.getWritableDatabase(), false);
		if(res){
			cacheTopStandardFilters.add(filter);
			sortListOfFilters(cacheTopStandardFilters);
		}
		helper.close();
		return res;
	}
	
	
	
	public boolean editPoiFilter(PoiLegacyFilter filter) {
		if (filter.getFilterId().equals(PoiLegacyFilter.CUSTOM_FILTER_ID) || 
				filter.getFilterId().equals(PoiLegacyFilter.BY_NAME_FILTER_ID) || filter.getFilterId().startsWith(PoiLegacyFilter.STD_PREFIX)) {
			return false;
		}
		PoiFilterDbHelper helper = openDbHelper();
		if (helper != null) {
			boolean res = helper.editFilter(helper.getWritableDatabase(), filter);
			helper.close();
			return res;
		}
		return false;
	}
	
	
	public class PoiFilterDbHelper  {

		public static final String DATABASE_NAME = "poi_filters"; //$NON-NLS-1$
	    private static final int DATABASE_VERSION = 5;
	    private static final String FILTER_NAME = "poi_filters"; //$NON-NLS-1$
	    private static final String FILTER_COL_NAME = "name"; //$NON-NLS-1$
	    private static final String FILTER_COL_ID = "id"; //$NON-NLS-1$
	    private static final String FILTER_COL_FILTERBYNAME = "filterbyname"; //$NON-NLS-1$
	    private static final String FILTER_TABLE_CREATE =   "CREATE TABLE " + FILTER_NAME + " (" + //$NON-NLS-1$ //$NON-NLS-2$
	    FILTER_COL_NAME + ", " + FILTER_COL_ID + ", " +  FILTER_COL_FILTERBYNAME + ");"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
	    
	    
	    private static final String CATEGORIES_NAME = "categories"; //$NON-NLS-1$
	    private static final String CATEGORIES_FILTER_ID = "filter_id"; //$NON-NLS-1$
	    private static final String CATEGORIES_COL_CATEGORY = "category"; //$NON-NLS-1$
	    private static final String CATEGORIES_COL_SUBCATEGORY = "subcategory"; //$NON-NLS-1$
	    private static final String CATEGORIES_TABLE_CREATE =   "CREATE TABLE " + CATEGORIES_NAME + " (" + //$NON-NLS-1$ //$NON-NLS-2$
	    CATEGORIES_FILTER_ID + ", " + CATEGORIES_COL_CATEGORY + ", " +  CATEGORIES_COL_SUBCATEGORY + ");"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		private OsmandApplication context;
		private SQLiteConnection conn;
		private MapPoiTypes mapPoiTypes;

	    PoiFilterDbHelper(MapPoiTypes mapPoiTypes, OsmandApplication context) {
			this.mapPoiTypes = mapPoiTypes;
			this.context = context;
	    }
	    
	    public SQLiteConnection getWritableDatabase() {
	    	return openConnection(false);
		}

		public void close() {
			if(conn != null) {
				conn.close();
				conn = null;
			}
		}

		public SQLiteConnection getReadableDatabase() {
	    	return openConnection(true);
	    }

		private SQLiteConnection openConnection(boolean readonly) {
			conn = context.getSQLiteAPI().getOrCreateDatabase(DATABASE_NAME, readonly);
			if (conn.getVersion() == 0 || DATABASE_VERSION != conn.getVersion()) {
				if (readonly) {
					conn.close();
					conn = context.getSQLiteAPI().getOrCreateDatabase(DATABASE_NAME, readonly);
				}
				if (conn.getVersion() == 0) {
					conn.setVersion(DATABASE_VERSION);
					onCreate(conn);
				} else {
					onUpgrade(conn, conn.getVersion(), DATABASE_VERSION);
				}

			}
			return conn;
		}

		public void onCreate(SQLiteConnection conn) {
	        conn.execSQL(FILTER_TABLE_CREATE);
	        conn.execSQL(CATEGORIES_TABLE_CREATE);
	    }

		
		public void onUpgrade(SQLiteConnection conn, int oldVersion, int newVersion) {
			if(newVersion <= 5) {
				deleteOldFilters(conn);
			}
			conn.setVersion(newVersion);
		}
	    
	    private void deleteOldFilters(SQLiteConnection conn) {
			for (String toDel : DEL) {
				deleteFilter(conn, "user_" + toDel);
			}			
		}

		protected boolean addFilter(PoiLegacyFilter p, SQLiteConnection db, boolean addOnlyCategories){
	    	if(db != null){
	    		if(!addOnlyCategories){
	    			db.execSQL("INSERT INTO " + FILTER_NAME + " VALUES (?, ?, ?)",new Object[]{p.getName(), p.getFilterId(), p.getFilterByName()}); //$NON-NLS-1$ //$NON-NLS-2$
	    		}
	    		Map<PoiCategory, LinkedHashSet<String>> types = p.getAcceptedTypes();
	    		SQLiteStatement insertCategories = db.compileStatement("INSERT INTO " +  CATEGORIES_NAME + " VALUES (?, ?, ?)"); //$NON-NLS-1$ //$NON-NLS-2$
	    		for(PoiCategory a : types.keySet()){
	    			if(types.get(a) == null){
		    			insertCategories.bindString(1, p.getFilterId());
						insertCategories.bindString(2, a.getKeyName());
						insertCategories.bindNull(3);
    					insertCategories.execute();
	    			} else {
	    				for(String s : types.get(a)){
	    					insertCategories.bindString(1, p.getFilterId());
	    					insertCategories.bindString(2, a.getKeyName());
	    					insertCategories.bindString(3, s);
	    					insertCategories.execute();
	    				}
	    			}
	    		}
	    		insertCategories.close();
	    		return true;
	    	}
	    	return false;
	    }
	    
	    protected List<PoiLegacyFilter> getFilters(SQLiteConnection conn){
	    	ArrayList<PoiLegacyFilter> list = new ArrayList<PoiLegacyFilter>();
	    	if(conn != null){
	    		SQLiteCursor query = conn.rawQuery("SELECT " + CATEGORIES_FILTER_ID +", " + CATEGORIES_COL_CATEGORY +"," + CATEGORIES_COL_SUBCATEGORY +" FROM " +  //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
	    				CATEGORIES_NAME, null);
	    		Map<String, Map<PoiCategory, LinkedHashSet<String>>> map = new LinkedHashMap<String, Map<PoiCategory,LinkedHashSet<String>>>();
	    		if(query.moveToFirst()){
	    			do {
	    				String filterId = query.getString(0);
	    				if(!map.containsKey(filterId)){
	    					map.put(filterId, new LinkedHashMap<PoiCategory, LinkedHashSet<String>>());
	    				}
	    				Map<PoiCategory, LinkedHashSet<String>> m = map.get(filterId);
	    				PoiCategory a = mapPoiTypes.getPoiCategoryByName(query.getString(1).toLowerCase(), false);
	    				String subCategory = query.getString(2);
	    				if(subCategory == null){
	    					m.put(a, null);
	    				} else {
	    					if(m.get(a) == null){
	    						m.put(a, new LinkedHashSet<String>());
	    					}
	    					m.get(a).add(subCategory);
	    				}
	    			} while(query.moveToNext());
	    		}
	    		query.close();
	    		
	    		query = conn.rawQuery("SELECT " + FILTER_COL_ID +", " + FILTER_COL_NAME +"," + FILTER_COL_FILTERBYNAME +" FROM " +  //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
	    				FILTER_NAME, null);
	    		if(query.moveToFirst()){
	    			do {
	    				String filterId = query.getString(0);
	    				if(map.containsKey(filterId)){
	    					PoiLegacyFilter filter = new PoiLegacyFilter(query.getString(1), filterId,
	    							map.get(filterId), application);
	    					filter.setFilterByName(query.getString(2));
	    					list.add(filter);
	    				}
	    			} while(query.moveToNext());
	    		}
	    		query.close();
	    	}
	    	return list;
	    }
	    
	    protected boolean editFilter(SQLiteConnection conn, PoiLegacyFilter filter) {
			if (conn != null) {
				conn.execSQL("DELETE FROM " + CATEGORIES_NAME + " WHERE " + CATEGORIES_FILTER_ID + " = ?",  //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
						new Object[] { filter.getFilterId() });
				addFilter(filter, conn, true);
				updateName(conn, filter);
				return true;
			}
			return false;
		}

		private void updateName(SQLiteConnection db, PoiLegacyFilter filter) {
			db.execSQL("UPDATE " + FILTER_NAME + " SET " + FILTER_COL_FILTERBYNAME + " = ?, " + FILTER_COL_NAME + " = ? " + " WHERE " //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
					+ FILTER_COL_ID + "= ?", new Object[] { filter.getFilterByName(), filter.getName(), filter.getFilterId() }); //$NON-NLS-1$
		}
	    
	    protected boolean deleteFilter(SQLiteConnection db, PoiLegacyFilter p){
	    	String key = p.getFilterId();
	    	return deleteFilter(db, key);
	    }

		private boolean deleteFilter(SQLiteConnection db, String key) {
			if (db != null) {
				db.execSQL("DELETE FROM " + FILTER_NAME + " WHERE " + FILTER_COL_ID + " = ?", new Object[] { key }); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
				db.execSQL(
						"DELETE FROM " + CATEGORIES_NAME + " WHERE " + CATEGORIES_FILTER_ID + " = ?", new Object[] { key }); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
				return true;
			}
			return false;
		}
	    


	}

}
