package com.berniesanders.fieldthebern.screens;

import android.os.Bundle;

import com.berniesanders.fieldthebern.FTBApplication;
import com.berniesanders.fieldthebern.R;
import com.berniesanders.fieldthebern.annotations.Layout;
import com.berniesanders.fieldthebern.controllers.ActionBarController;
import com.berniesanders.fieldthebern.controllers.ActionBarService;
import com.berniesanders.fieldthebern.dagger.FtbScreenScope;
import com.berniesanders.fieldthebern.dagger.MainComponent;
import com.berniesanders.fieldthebern.mortar.FlowPathBase;
import com.berniesanders.fieldthebern.views.LearnView;

import javax.inject.Inject;

import butterknife.BindString;
import butterknife.ButterKnife;
import mortar.ViewPresenter;
import timber.log.Timber;


@Layout(R.layout.screen_learn)
public class LearnScreen extends FlowPathBase {


    public LearnScreen() {
    }

    @Override
    public Object createComponent() {
        return DaggerLearnScreen_Component
                .builder()
                .mainComponent(FTBApplication.getComponent())
                .learnModule(new LearnModule())
                .build();
    }

    @Override
    public String getScopeName() {
        return LearnScreen.class.getName();
    }


    @dagger.Module
    class LearnModule {

        public LearnModule() {
        }

    }

    @FtbScreenScope
    @dagger.Component(modules = LearnModule.class, dependencies = MainComponent.class)
    public interface Component {
        void inject(LearnView t);
    }

    @FtbScreenScope
    static public class Presenter extends ViewPresenter<LearnView> {

        @BindString(R.string.learn_screen_title) String screenTitleString;

        @Inject
        Presenter() {
        }

        @Override
        protected void onLoad(Bundle savedInstanceState) {
            Timber.v("onLoad");
            ButterKnife.bind(this, getView());
            setActionBar();
        }

        void setActionBar() {
            ActionBarService
                    .get(getView())
                    .closeAppbar()
                    .setMainImage(null)
                    .setConfig(new ActionBarController.Config(screenTitleString, null));
        }

        @Override
        protected void onSave(Bundle outState) {
        }

        @Override
        public void dropView(LearnView view) {
            super.dropView(view);
            ButterKnife.unbind(this);
        }

    }
}