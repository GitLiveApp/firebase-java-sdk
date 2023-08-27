package com.google.firebase.auth;

import androidx.annotation.Keep;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.internal.InternalAuthProvider;
import com.google.firebase.components.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Keep
public class FirebaseAuthRegistrar implements ComponentRegistrar  {
    @Keep
    public List<Component<?>> getComponents() {
        return Arrays.asList(new Component[]{
                Component.builder(FirebaseAuth.class, new Class[]{ InternalAuthProvider.class })
                    .add(Dependency.required(FirebaseApp.class))
                    .factory(componentContainer -> {
                        FirebaseApp app = componentContainer.get(FirebaseApp.class);
                        FirebaseAuth auth = new FirebaseAuth(app);
                        return auth;
                    }).alwaysEager().build()
        });
    }
}
