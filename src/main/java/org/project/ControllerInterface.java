package org.project;

import java.io.FileNotFoundException;

public interface ControllerInterface extends Runnable
{
    void run();
    void Serialize();
    ControllerInterface Deserialize() throws FileNotFoundException;

    void SetSnapshotCreator(SnapshotCreator snapshotCreator);
}
