package pasta.streamer.activities;

import android.app.ActivityManager;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.databinding.DataBindingUtil;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.ColorInt;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.AppBarLayout;
import android.support.design.widget.CoordinatorLayout;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.content.ContextCompat;
import android.support.v4.graphics.drawable.DrawableCompat;
import android.support.v4.util.ArrayMap;
import android.support.v4.view.MenuItemCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.SearchView;
import android.support.v7.widget.Toolbar;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.afollestad.async.Action;
import com.afollestad.async.Async;
import com.afollestad.async.Done;
import com.afollestad.async.Pool;
import com.afollestad.async.Result;
import com.afollestad.materialdialogs.color.ColorChooserDialog;
import com.mikepenz.materialdrawer.AccountHeader;
import com.mikepenz.materialdrawer.AccountHeaderBuilder;
import com.mikepenz.materialdrawer.Drawer;
import com.mikepenz.materialdrawer.DrawerBuilder;
import com.mikepenz.materialdrawer.model.DividerDrawerItem;
import com.mikepenz.materialdrawer.model.ProfileDrawerItem;
import com.mikepenz.materialdrawer.model.SecondaryDrawerItem;
import com.mikepenz.materialdrawer.model.interfaces.IDrawerItem;
import com.mikepenz.materialdrawer.util.DrawerUIUtils;

import java.util.ArrayList;
import java.util.Map;

import butterknife.Bind;
import butterknife.ButterKnife;
import kaaes.spotify.webapi.android.models.Album;
import kaaes.spotify.webapi.android.models.AlbumSimple;
import kaaes.spotify.webapi.android.models.AlbumsPager;
import kaaes.spotify.webapi.android.models.Artist;
import kaaes.spotify.webapi.android.models.ArtistsPager;
import kaaes.spotify.webapi.android.models.PlaylistSimple;
import kaaes.spotify.webapi.android.models.PlaylistsPager;
import kaaes.spotify.webapi.android.models.Track;
import kaaes.spotify.webapi.android.models.TracksPager;
import pasta.streamer.Pasta;
import pasta.streamer.R;
import pasta.streamer.data.AlbumListData;
import pasta.streamer.data.ArtistListData;
import pasta.streamer.data.PlaylistListData;
import pasta.streamer.data.TrackListData;
import pasta.streamer.fragments.AboutFragment;
import pasta.streamer.fragments.AlbumFragment;
import pasta.streamer.fragments.ArtistFragment;
import pasta.streamer.fragments.CategoriesFragment;
import pasta.streamer.fragments.FabFragment;
import pasta.streamer.fragments.FavoritesFragment;
import pasta.streamer.fragments.FullScreenFragment;
import pasta.streamer.fragments.HomeFragment;
import pasta.streamer.fragments.SearchFragment;
import pasta.streamer.fragments.SettingsFragment;
import pasta.streamer.utils.Downloader;
import pasta.streamer.utils.Settings;
import pasta.streamer.utils.StaticUtils;
import pasta.streamer.views.Playbar;

public class HomeActivity extends AppCompatActivity implements ColorChooserDialog.ColorCallback {

    @Bind(R.id.playbar)
    View playbarView;
    @Bind(R.id.toolbar)
    Toolbar toolbar;
    @Bind(R.id.appbar)
    AppBarLayout appbar;
    @Bind(R.id.coordinator)
    CoordinatorLayout coordinatorLayout;
    @Nullable @Bind(R.id.drawer_layout)
    DrawerLayout drawer_layout;
    @Bind(R.id.drawer)
    FrameLayout drawer_container;
    @Bind(R.id.fab)
    FloatingActionButton fab;
    @Bind(R.id.status_background)
    FrameLayout statusBackground;

    private Playbar playbar;
    private Drawer materialDrawer;

    private Fragment f;
    private long selected;
    private int title = R.string.title_activity_home;
    private Map<String, Object> limitMap;
    private Pool searchPool;
    private ArrayList searchDatas;
    private boolean preload;
    private Pasta pasta;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (Settings.isDarkTheme(this)) setTheme(R.style.AppTheme_Transparent_Dark);
        DataBindingUtil.setContentView(this, R.layout.activity_home);
        ButterKnife.bind(this);

        limitMap = new ArrayMap<>();
        limitMap.put("limit", (Settings.getLimit(this) + 1) * 10);

        preload = Settings.isPreload(this);
        pasta = (Pasta) getApplicationContext();

        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setHomeAsUpIndicator(R.drawable.drawer_toggle);

        fab.setBackgroundTintList(ColorStateList.valueOf(Settings.getAccentColor(this)));

        Drawable home = ContextCompat.getDrawable(this, R.drawable.ic_home);
        Drawable fav = ContextCompat.getDrawable(this, R.drawable.ic_fav);
        Drawable bookmark = ContextCompat.getDrawable(this, R.drawable.ic_bookmark);
        Drawable playing = ContextCompat.getDrawable(this, R.drawable.ic_now_playing);
        Drawable settings = ContextCompat.getDrawable(this, R.drawable.ic_settings);

        int tint = ContextCompat.getColor(this, R.color.material_drawer_primary_icon);
        DrawableCompat.setTint(home, tint);
        DrawableCompat.setTint(fav, tint);
        DrawableCompat.setTint(bookmark, tint);
        DrawableCompat.setTint(playing, tint);
        DrawableCompat.setTint(settings, tint);

        DrawerBuilder builder = new DrawerBuilder()
                .withActivity(this)
                .withFullscreen(true)
                .withToolbar(toolbar)
                .withAccountHeader(getAccountHeader())
                .withSelectedItem(0)
                .addDrawerItems(
                        new SecondaryDrawerItem().withName(R.string.title_activity_home).withIdentifier(1).withIcon(home),
                        new SecondaryDrawerItem().withName(R.string.title_activity_favorites).withIdentifier(2).withIcon(fav),
                        new SecondaryDrawerItem().withName(R.string.title_activity_categories).withIdentifier(3).withIcon(bookmark),
                        new SecondaryDrawerItem().withName(R.string.title_activity_playing).withIdentifier(4).withIcon(playing).withSelectable(false),
                        new DividerDrawerItem(),
                        new SecondaryDrawerItem().withName(R.string.title_activity_settings).withIdentifier(5).withIcon(settings),
                        new SecondaryDrawerItem().withName(R.string.title_about).withIdentifier(6)
                )
                .withOnDrawerItemClickListener(new com.mikepenz.materialdrawer.Drawer.OnDrawerItemClickListener() {
                    @Override
                    public boolean onItemClick(View view, int position, IDrawerItem drawerItem) {
                        if (selected == drawerItem.getIdentifier()) return false;

                        switch (((int) drawerItem.getIdentifier())) {
                            case 1:
                                f = new HomeFragment();
                                title = R.string.title_activity_home;
                                break;
                            case 2:
                                f = new FavoritesFragment();
                                title = R.string.title_activity_favorites;
                                break;
                            case 3:
                                f = new CategoriesFragment();
                                title = R.string.title_activity_categories;
                                break;
                            case 4:
                                if (!playbar.playing) {
                                    Snackbar snackbar = Snackbar.make(coordinatorLayout, "Nothing is playing...", Snackbar.LENGTH_SHORT);

                                    CoordinatorLayout.LayoutParams params = (CoordinatorLayout.LayoutParams) snackbar.getView().getLayoutParams();
                                    params.bottomMargin = getResources().getDimensionPixelSize(R.dimen.playbar_size);
                                    snackbar.getView().setLayoutParams(params);

                                    snackbar.show();

                                    if (drawer_layout != null) drawer_layout.closeDrawer(Gravity.LEFT);
                                    return false;
                                }

                                startActivity(new Intent(HomeActivity.this, PlayerActivity.class));
                                return true;
                            case 5:
                                f = new SettingsFragment();
                                title = R.string.title_activity_settings;
                                break;
                            case 6:
                                f = new AboutFragment();
                                title = R.string.title_activity_about;
                                break;
                            default:
                                return false;
                        }

                        selected = drawerItem.getIdentifier();
                        getSupportFragmentManager().beginTransaction().replace(R.id.fragment, f).commit();

                        setListeners(f);

                        if (drawer_layout != null) drawer_layout.closeDrawer(Gravity.LEFT);
                        return true;
                    }
                });

        materialDrawer = builder.buildView();
        View v = materialDrawer.getSlider();
        v.setLayoutParams(new ViewGroup.LayoutParams(DrawerUIUtils.getOptimalDrawerWidth(this), ViewGroup.LayoutParams.MATCH_PARENT));
        drawer_container.addView(v);

        playbar = new Playbar(this);
        playbar.initPlayBar(playbarView);
        playbar.setPlaybarListener(new Playbar.PlaybarListener() {
            @Override
            public void onHide(boolean hidden) {
                CoordinatorLayout.LayoutParams layoutParams = (CoordinatorLayout.LayoutParams) fab.getLayoutParams();
                layoutParams.bottomMargin = hidden ? (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 16, getResources().getDisplayMetrics()) : getResources().getDimensionPixelSize(R.dimen.bottom_playbar_padding);
                fab.setLayoutParams(layoutParams);
            }
        });

        getSupportFragmentManager().addOnBackStackChangedListener(new FragmentManager.OnBackStackChangedListener() {
            @Override
            public void onBackStackChanged() {
                HomeActivity.this.f = getSupportFragmentManager().findFragmentById(R.id.fragment);
                setListeners(f);
            }
        });

        if (savedInstanceState != null) {
            f = getSupportFragmentManager().findFragmentById(R.id.fragment);
            if (f != null) {
                setListeners(f);
                return;
            }
        }

        f = new HomeFragment();
        getSupportFragmentManager().beginTransaction().add(R.id.fragment, f).commit();

        if (getIntent().getParcelableExtra("artist") != null) {
            Bundle args = new Bundle();
            args.putParcelable("artist", getIntent().getParcelableExtra("artist"));
            f = new ArtistFragment();
            f.setArguments(args);
            getSupportFragmentManager().beginTransaction().add(R.id.fragment, f).addToBackStack(null).commit();
        } else if (getIntent().getParcelableExtra("album") != null) {
            Bundle args = new Bundle();
            args.putParcelable("album", getIntent().getParcelableExtra("album"));
            f = new AlbumFragment();
            f.setArguments(args);
            getSupportFragmentManager().beginTransaction().add(R.id.fragment, f).addToBackStack(null).commit();
        }

        setListeners(f);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (playbar != null) playbar.registerPlaybar();
    }

    @Override
    public void onPause() {
        super.onPause();
        if (playbar != null) playbar.unregisterPlaybar();
    }

    public void setListeners(Fragment f) {
        if (f instanceof FullScreenFragment) {
            appbar.setExpanded(false, false);
            fab.hide();

            ((FullScreenFragment) f).setDataListener(new FullScreenFragment.DataListener(){
                @Override
                public void onDataReady(String title, int statusColor, int windowColor) {
                    setTitle(title);
                    statusBackground.setBackgroundColor(StaticUtils.darkColor(statusColor));
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        ActivityManager.TaskDescription desc = new ActivityManager.TaskDescription(getTitle().toString(), StaticUtils.drawableToBitmap(ContextCompat.getDrawable(HomeActivity.this, R.mipmap.ic_launcher)), windowColor);
                        setTaskDescription(desc);
                    }
                }
            });

            materialDrawer.setSelection(-1, false);
        } else {
            appbar.setExpanded(true, false);

            setTitle(title);
            if (f instanceof FavoritesFragment) materialDrawer.setSelection(2, false);
            else if (f instanceof CategoriesFragment) materialDrawer.setSelection(3, false);
            else if (f instanceof SettingsFragment) materialDrawer.setSelection(5, false);
            else if (f instanceof AboutFragment) materialDrawer.setSelection(6, false);

            statusBackground.setBackgroundColor(StaticUtils.darkColor(Settings.getPrimaryColor(this)));

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                ActivityManager.TaskDescription desc = new ActivityManager.TaskDescription(getTitle().toString(), StaticUtils.drawableToBitmap(ContextCompat.getDrawable(this, R.mipmap.ic_launcher)), Settings.getPrimaryColor(this));
                setTaskDescription(desc);
            }

            if (f instanceof FabFragment) {
                ((FabFragment) f).setFabListener(new FabFragment.FabListener() {
                    @Override
                    public void onDataReady(boolean visible, int iconRes, View.OnClickListener clickListener) {
                        if (visible) fab.show();
                        else fab.hide();
                        fab.setImageResource(iconRes);
                        fab.setOnClickListener(clickListener);
                    }
                });
            } else fab.hide();

            if (f instanceof SearchFragment || f instanceof CategoriesFragment || f instanceof SettingsFragment || f instanceof AboutFragment) {
                appbar.setTargetElevation(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 4, getResources().getDisplayMetrics()));
            } else appbar.setTargetElevation(0f);

            if (f instanceof SearchFragment && !searchPool.isExecuting() && (searchDatas != null && searchDatas.size() > 0)) {
                ((SearchFragment) f).swapData(searchDatas);
            }
        }

        if (searchPool != null && searchPool.isExecuting()) searchPool.cancel();
    }

    private AccountHeader getAccountHeader() {
        ProfileDrawerItem profile;
        if (pasta.me == null) {
            StaticUtils.onNetworkError(this);
            return null;
        }

        try {
            String url = pasta.me.images.get(0).url;
            profile = new ProfileDrawerItem().withName(pasta.me.display_name.length() > 0 ? pasta.me.display_name : pasta.me.email.split("@")[0].toUpperCase()).withEmail(pasta.me.email).withIcon(Downloader.downloadImage(this, url));
        } catch (Exception e) {
            profile = new ProfileDrawerItem().withName(pasta.me.email.split("@")[0].toUpperCase()).withEmail(pasta.me.email);
        }

        if (profile != null) {
            return new AccountHeaderBuilder()
                    .withActivity(this)
                    .withCompactStyle(false)
                    .withHeaderBackground(R.mipmap.drawer_bg)
                    .withProfileImagesClickable(false)
                    .withSelectionListEnabledForSingleProfile(false)
                    .addProfiles(profile)
                    .build();
        } else {
            return new AccountHeaderBuilder()
                    .withActivity(this)
                    .withCompactStyle(false)
                    .withHeaderBackground(R.mipmap.drawer_bg)
                    .withProfileImagesClickable(false)
                    .withSelectionListEnabledForSingleProfile(false)
                    .build();
        }
    }

    private void search(final String searchTerm, final boolean pre) {
        if (searchTerm == null || searchTerm.length() < 1) return;

        if (!(f instanceof SearchFragment)) {
            f = new SearchFragment();
            getSupportFragmentManager().beginTransaction().replace(R.id.fragment, f).addToBackStack(null).commit();
        }

        ((SearchFragment) f).clear();

        if (searchPool != null && searchPool.isExecuting()) searchPool.cancel();
        searchPool = Async.parallel(new Action<ArrayList<TrackListData>>() {
            @NonNull
            @Override
            public String id() {
                return "searchTracks";
            }

            @Nullable
            @Override
            protected ArrayList<TrackListData> run() throws InterruptedException {
                ArrayList<TrackListData> list = new ArrayList<>();
                TracksPager tracksPager;

                try {
                    tracksPager = pasta.spotifyService.searchTracks(searchTerm, limitMap);
                } catch (Exception e) {
                    e.printStackTrace();
                    return null;
                }

                for (Track track : tracksPager.tracks.items) {
                    TrackListData trackData = new TrackListData(track);
                    list.add(trackData);
                }

                return list;
            }

            @Override
            protected void done(@Nullable ArrayList<TrackListData> result) {
                if (result == null || pre) return;
                ((SearchFragment) f).addData(result);
            }
        }, new Action<ArrayList<String>>() {
            @NonNull
            @Override
            public String id() {
                return "searchAlbums";
            }

            @Nullable
            @Override
            protected ArrayList<String> run() throws InterruptedException {
                ArrayList<String> list = new ArrayList<>();
                AlbumsPager albumsPager;

                try {
                    albumsPager = pasta.spotifyService.searchAlbums(searchTerm, limitMap);
                } catch (Exception e) {
                    e.printStackTrace();
                    return null;
                }

                for (AlbumSimple album : albumsPager.albums.items) {
                    list.add(album.id);
                }

                return list;
            }

            @Override
            protected void done(@Nullable ArrayList<String> result) {
                if (result == null || pre) return;
                for (final String id : result) {
                    new Action<AlbumListData>() {
                        @NonNull
                        @Override
                        public String id() {
                            return "getAlbum";
                        }

                        @Nullable
                        @Override
                        protected AlbumListData run() throws InterruptedException {
                            Album album;
                            try {
                                album = pasta.spotifyService.getAlbum(id);
                            } catch (Exception e) {
                                e.printStackTrace();
                                return null;
                            }

                            Artist artist;
                            try {
                                artist = pasta.spotifyService.getArtist(album.artists.get(0).id);
                            } catch (Exception e) {
                                e.printStackTrace();
                                return null;
                            }

                            String image;
                            try {
                                image = artist.images.get(album.images.size() / 2).url;
                            } catch (IndexOutOfBoundsException e) {
                                image = "";
                            }
                            return new AlbumListData(album, image);
                        }

                        @Override
                        protected void done(@Nullable AlbumListData result) {
                            if (result == null) return;
                            ((SearchFragment) f).addData(result);
                        }
                    }.execute();
                }
            }
        }, new Action<ArrayList<PlaylistListData>>() {
            @NonNull
            @Override
            public String id() {
                return "searchPlaylists";
            }

            @Nullable
            @Override
            protected ArrayList<PlaylistListData> run() throws InterruptedException {
                ArrayList<PlaylistListData> list = new ArrayList<>();
                PlaylistsPager playlistsPager;

                try {
                    playlistsPager = pasta.spotifyService.searchPlaylists(searchTerm, limitMap);
                } catch (Exception e) {
                    e.printStackTrace();
                    return null;
                }

                for (PlaylistSimple playlist : playlistsPager.playlists.items) {
                    PlaylistListData playlistData = new PlaylistListData(playlist, pasta.me);
                    list.add(playlistData);
                }

                return list;
            }

            @Override
            protected void done(@Nullable ArrayList<PlaylistListData> result) {
                if (result == null || pre) return;
                ((SearchFragment) f).addData(result);
            }
        }, new Action<ArrayList<ArtistListData>>() {
            @NonNull
            @Override
            public String id() {
                return "searchArtists";
            }

            @Nullable
            @Override
            protected ArrayList<ArtistListData> run() throws InterruptedException {
                ArrayList<ArtistListData> list = new ArrayList<>();
                ArtistsPager artistsPager;

                try {
                    artistsPager = pasta.spotifyService.searchArtists(searchTerm, limitMap);
                } catch (Exception e) {
                    e.printStackTrace();
                    return null;
                }

                for (Artist artist : artistsPager.artists.items) {
                    ArtistListData artistData = new ArtistListData(artist);
                    list.add(artistData);
                }

                return list;
            }

            @Override
            protected void done(@Nullable ArrayList<ArtistListData> result) {
                if (result == null || pre) return;
                ((SearchFragment) f).addData(result);
            }
        }).done(new Done() {
            @Override
            public void result(@NonNull Result result) {
                if (!pre) return;
                searchDatas = new ArrayList();

                Action<?> tracksResult = result.get("searchTracks");
                Action<?> playlistsResult = result.get("searchPlaylists");
                Action<?> artistsResult = result.get("searchArtists");
                Action<?> albumsResult = result.get("searchAlbums");

                if (tracksResult != null && tracksResult.getResult() != null) searchDatas.addAll((ArrayList<TrackListData>) tracksResult.getResult());
                if (playlistsResult != null && playlistsResult.getResult() != null) searchDatas.addAll((ArrayList<PlaylistListData>) playlistsResult.getResult());
                if (artistsResult != null && artistsResult.getResult() != null) searchDatas.addAll((ArrayList<ArtistListData>) artistsResult.getResult());

                ((SearchFragment) f).swapData(searchDatas);

                if (albumsResult != null && albumsResult.getResult() != null) {
                    ArrayList<String> results = (ArrayList<String>) albumsResult.getResult();
                    Action[] varargs = new Action[results.size()];

                    for (int i = 0; i < results.size(); i++) {
                        final String id = results.get(i);
                        varargs[i] = new Action<AlbumListData>() {
                            @NonNull
                            @Override
                            public String id() {
                                return "getAlbum";
                            }

                            @Nullable
                            @Override
                            protected AlbumListData run() throws InterruptedException {
                                Album album;
                                try {
                                    album = pasta.spotifyService.getAlbum(id);
                                } catch (Exception e) {
                                    e.printStackTrace();
                                    return null;
                                }

                                Artist artist;
                                try {
                                    artist = pasta.spotifyService.getArtist(album.artists.get(0).id);
                                } catch (Exception e) {
                                    e.printStackTrace();
                                    return null;
                                }

                                String image;
                                try {
                                    image = artist.images.get(album.images.size() / 2).url;
                                } catch (IndexOutOfBoundsException e) {
                                    image = "";
                                }
                                return new AlbumListData(album, image);
                            }

                            @Override
                            protected void done(@Nullable AlbumListData result) {
                                if (result == null) return;
                                searchDatas.add(result);
                                ((SearchFragment) f).addData(result);
                            }
                        };
                    }

                    searchPool = Async.parallel(varargs);
                }
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_home, menu);

        SearchView searchView = (SearchView) MenuItemCompat.getActionView(menu.findItem(R.id.action_search));

        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                search(query, false);
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                if (preload) search(newText, true);
                return true;
            }
        });

        searchView.setOnCloseListener(new SearchView.OnCloseListener() {
            @Override
            public boolean onClose() {
                if (f instanceof SearchFragment) onBackPressed();
                return false;
            }
        });

        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
         if (item.getItemId() == android.R.id.home) {
             if (drawer_layout != null) drawer_layout.openDrawer(Gravity.LEFT);
         }
        return false;
    }

    @Override
    public void onColorSelection(@NonNull ColorChooserDialog dialog, @ColorInt int selectedColor) {
        if (f instanceof SettingsFragment) ((SettingsFragment) f).onColorSelection(dialog, selectedColor);
    }
}
