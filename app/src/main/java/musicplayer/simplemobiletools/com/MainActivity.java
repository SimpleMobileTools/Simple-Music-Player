package musicplayer.simplemobiletools.com;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;

import butterknife.ButterKnife;
import butterknife.OnClick;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);
    }

    @OnClick(R.id.previousBtn)
    public void previousClicked() {

    }

    @OnClick(R.id.playPauseBtn)
    public void playPauseClicked() {

    }

    @OnClick(R.id.nextBtn)
    public void nextClicked() {

    }

    @OnClick(R.id.stopBtn)
    public void stopClicked() {

    }
}
