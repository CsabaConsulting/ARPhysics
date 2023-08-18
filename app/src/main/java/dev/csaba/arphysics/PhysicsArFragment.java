package dev.csaba.arphysics;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.snackbar.Snackbar;
import com.google.ar.sceneform.ux.ArFragment;

public class PhysicsArFragment extends ArFragment {
    private static final String TAG = "PhysicsArFragment";

    // Do a runtime check for the OpenGL level available at runtime to avoid Sceneform crashing the
    // application.
    private static final double MIN_OPENGL_VERSION = 3.0;

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);

        View contentView = requireActivity().findViewById(android.R.id.content);

        String openGlVersionString =
                ((ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE))
                        .getDeviceConfigurationInfo()
                        .getGlEsVersion();
        if (Double.parseDouble(openGlVersionString) < MIN_OPENGL_VERSION) {
            String openGLCheckMessage = "Sceneform requires OpenGL ES 3.0 or later";
            Log.e(TAG, openGLCheckMessage);
            Snackbar.make(contentView, openGLCheckMessage, Snackbar.LENGTH_LONG)
                    .setAction("Action", null).show();
        }
    }

    @Override
    public View onCreateView(
            LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = super.onCreateView(inflater, container, savedInstanceState);

        // Turn off the plane discovery since we're only looking for images
        // I think this was for the image activation, we may need this here!
        getInstructionsController().setEnabled(false);
        getInstructionsController().setVisible(false);
        getArSceneView().getPlaneRenderer().setEnabled(false);
        return view;
    }
}
