package com.berniesanders.fieldthebern.screens;

import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.location.Location;
import android.os.Bundle;

import com.berniesanders.fieldthebern.FTBApplication;
import com.berniesanders.fieldthebern.R;
import com.berniesanders.fieldthebern.annotations.Layout;
import com.berniesanders.fieldthebern.controllers.ErrorToastService;
import com.berniesanders.fieldthebern.controllers.LocationService;
import com.berniesanders.fieldthebern.dagger.FtbScreenScope;
import com.berniesanders.fieldthebern.controllers.ActionBarController;
import com.berniesanders.fieldthebern.controllers.ActionBarService;
import com.berniesanders.fieldthebern.dagger.MainComponent;
import com.berniesanders.fieldthebern.media.SaveImageTarget;
import com.berniesanders.fieldthebern.models.CreateUserRequest;
import com.berniesanders.fieldthebern.models.User;
import com.berniesanders.fieldthebern.models.UserAttributes;
import com.berniesanders.fieldthebern.mortar.FlowPathBase;
import com.berniesanders.fieldthebern.repositories.UserRepo;
import com.berniesanders.fieldthebern.repositories.specs.UserSpec;
import com.berniesanders.fieldthebern.views.SignupView;
import com.squareup.picasso.Picasso;

import org.apache.commons.lang3.StringUtils;

import javax.inject.Inject;

import butterknife.BindString;
import butterknife.ButterKnife;
import butterknife.OnClick;
import dagger.Provides;
import flow.Flow;
import mortar.ViewPresenter;
import retrofit.HttpException;
import rx.Observer;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;
import timber.log.Timber;

/**
 * Example for creating new Mortar Screen that helps explain how it all works
 *
 * Set the @Layout annotation to the resource id of the layout for the screen
 */
@Layout(R.layout.screen_signup)
public class SignupScreen extends FlowPathBase {

    private final UserAttributes userAttributes;

    /**
     */
    public SignupScreen(UserAttributes userAttributes) {
        this.userAttributes = userAttributes;
    }

    /**
     */
    @Override
    public Object createComponent() {
        return DaggerSignupScreen_Component
                .builder()
                .mainComponent(FTBApplication.getComponent())
                .module(new Module(userAttributes))
                .build();
    }

    /**
     */
    @Override
    public String getScopeName() {
        return SignupScreen.class.getName();
    }


    @dagger.Module
    class Module {
        private final UserAttributes userAttributes;

        public Module(UserAttributes userAttributes) {
            this.userAttributes = userAttributes;
        }

        @FtbScreenScope
        @Provides
        public UserAttributes provideUserAttributes() {
            return userAttributes;
        }
    }

    /**
     */
    @FtbScreenScope
    @dagger.Component(dependencies = MainComponent.class, modules = Module.class)
    public interface Component {
        void inject(SignupView t);
        UserRepo userRepo();
    }

    @FtbScreenScope
    static public class Presenter extends ViewPresenter<SignupView> {

        @BindString(R.string.signup_title) String screenTitleString;

        private final UserRepo repo;
        private UserAttributes userAttributes;
        Bitmap userBitmap;

        private String stateCode;
        private Location location;
        private Subscription stateCodeSubscription;
        private Subscription locationSubscription;

        private boolean stateCodeRequestCompleted = false;
        private boolean locationRequestCompleted = false;

        @Inject
        Presenter(UserRepo repo, UserAttributes userAttributes) {
            this.repo = repo;
            this.userAttributes = userAttributes;
        }

        @Override
        protected void onLoad(Bundle savedInstanceState) {
            Timber.v("onLoad");
            ButterKnife.bind(this, getView());
            setActionBar();

            if (userAttributes.isFacebookUser()) {
                getView().showFacebook(userAttributes);
                loadPhoto();
            }

            //call in case the photo loads while rotating
            showPhotoIfExists();
        }

        private void showPhotoIfExists() {

            if (userBitmap==null) { return; } //nothing to show

            if (getView() == null) { //nowhere to show
                Timber.w("showPhotoIfExists called but no photo was attached");
                return;
            }

            getView()
                    .getUserImageView()
                    .setImageDrawable(
                            new BitmapDrawable(getView().getContext().getResources(),
                                    userBitmap));
        }

        private void loadPhoto() {
            Picasso.with(getView().getContext())
                    .load(userAttributes.getPhotoLargeUrl())
                    .into(new SaveImageTarget(onLoad));
        }



        SaveImageTarget.OnLoad onLoad = new SaveImageTarget.OnLoad() {

            @Override
            public void onLoad(Bitmap bitmap, String encodedString) {
                userBitmap = bitmap;
                Timber.v("onLoad encodedString.length() = %d", encodedString.length());
                userAttributes.base64PhotoData(encodedString);
                showPhotoIfExists();
            }
        };

        void setActionBar() {
            ActionBarService
                    .getActionbarController(getView())
                    .showToolbar()
                    .closeAppbar()
                    .lockDrawer()
                    .setMainImage(null)
                    .setConfig(new ActionBarController.Config(screenTitleString, null));
        }

        @OnClick(R.id.submit)
        void onSubmit() {
            userAttributes = getView().getInput(userAttributes);
            requestLocation();
        }

        private boolean addressAndLocationSet() {
            return (stateCodeRequestCompleted && locationRequestCompleted);
        }

        private Observer<Location> locationObserver = new Observer<Location>() {
            @Override
            public void onCompleted() {
                locationRequestCompleted = true;
                if (addressAndLocationSet()) {
                    createEmailUser();
                }
            }

            @Override
            public void onError(Throwable e) {
                locationRequestCompleted = true;
                Timber.e(e, "locationObserver onError");
                if (addressAndLocationSet()) {
                    createEmailUser();
                }
            }

            @Override
            public void onNext(Location location) {
                Timber.v("location on next"+location);
                Presenter.this.location = location;
            }
        };

        private Observer<String> stateCodeObserver = new Observer<String>() {
            @Override
            public void onCompleted() {
                stateCodeRequestCompleted = true;
                if (addressAndLocationSet()) {
                    createEmailUser();
                }
            }

            @Override
            public void onError(Throwable e) {
                stateCodeRequestCompleted = true;
                Timber.e(e, "stateCodeObserver onError");
                if (addressAndLocationSet()) {
                    createEmailUser();
                }
            }

            @Override
            public void onNext(String stateCode) {
                Timber.v("stateCodeObserver on next"+stateCode);
                Presenter.this.stateCode = stateCode;
            }
        };

        private void requestLocation() {
            stateCodeSubscription = LocationService.get(getView()).getAddress().subscribe(stateCodeObserver);
            locationSubscription = LocationService.get(getView()).get().subscribe(locationObserver);;
        }

        private void createEmailUser() {

            //if validate form...
            Timber.v("createEmailUser");

            if (location!=null) {
                userAttributes
                        .lat(location.getLatitude())
                        .lng(location.getLongitude());
            }

            if (StringUtils.isNotBlank(stateCode)) {
                userAttributes
                        .stateCode(stateCode);
            }

            CreateUserRequest createUserRequest =
                    new CreateUserRequest().withAttributes(userAttributes);

            final UserSpec spec = new UserSpec().create(createUserRequest);

            repo.create(spec)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(observer);
        }

        Observer<User> observer = new Observer<User>() {
            @Override
            public void onCompleted() {
                Timber.d("createUserRequest done.");
                Flow.get(getView().getContext()).set(new HomeScreen());
            }

            @Override
            public void onError(Throwable e) {
                Timber.e(e, "createUserRequest error");
                if (e instanceof HttpException) {
                    ErrorToastService.get(getView()).showApiError(e);
                }
            }

            @Override
            public void onNext(User user) {
                Timber.d("user: %s", user.toString());
            }
        };


        @Override
        protected void onSave(Bundle outState) {
        }

        @Override
        public void dropView(SignupView view) {
            super.dropView(view);
            ButterKnife.unbind(this);
        }

        private void safeUnsubscribe(Subscription subscription) {
            if (subscription!=null) {
                subscription.unsubscribe();
            }
        }
    }
}