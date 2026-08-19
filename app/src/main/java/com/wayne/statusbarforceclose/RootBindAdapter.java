package com.wayne.statusbarforceclose;

interface RootBindAdapter {
    Binding bind(Listener listener);

    interface Binding {
        void unbind();
    }

    interface Listener {
        void connected(RootController controller);

        void disconnected();

        void denied();

        void incompatible();

        void failed(Throwable failure);
    }
}
