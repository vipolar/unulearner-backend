package com.unulearner.backend.storage.tasks;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;

import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;

import com.unulearner.backend.storage.StorageTree;
import com.unulearner.backend.storage.StorageTreeNode;

public class StorageTaskDeleteFile extends StorageTask {

    public StorageTaskDeleteFile(@NonNull StorageTree storageTree, @NonNull StorageTreeNode targetNode) {
        super(storageTree, targetNode);
        
        this.setCurrentTarget(targetNode);
        this.setOnConflictOptions(new ArrayList<>(Arrays.asList(
            new Option("skip", "Skip", true, true)
        )));

        final String exitMessage = "File removal task initiated on %s!".formatted(Instant.now().toString());
        this.setExitStatus(HttpStatus.ACCEPTED);
        this.setExitMessage(exitMessage);
        this.logMessage(exitMessage);
    }
    
    @Override
    public synchronized void run(String onConflictAction, Boolean onConflictActionIsPersistent, Boolean cancelTaskExecution) {
        String exitMessage = null;

        if (cancelTaskExecution != null && cancelTaskExecution == true) {
            exitMessage = "File removal task cancelled on %s!".formatted(Instant.now().toString());
            this.setExitStatus(HttpStatus.OK);
            this.setExitMessage(exitMessage);
            this.logMessage(exitMessage);
            this.setTaskAsDone();
            return;
        }

        //TODO: this is an error condition?
        final StorageTreeNode currentTarget = this.getCurrentTarget();
        if (currentTarget == null) {
            exitMessage = "File removal task finished successfully on %s!".formatted(Instant.now().toString());
            this.setExitStatus(HttpStatus.OK);
            this.setExitMessage(exitMessage);
            this.logMessage(exitMessage);
            this.setTaskAsDone();
            return;
        }

        final String onConflict = this.getOnConflict();
        if (onConflictAction != null) {
            onConflictActionIsPersistent = onConflictActionIsPersistent != null ? onConflictActionIsPersistent : false;
            this.setOnConflict(onConflictAction, onConflictActionIsPersistent);
        }

        try {
            if (onConflict != null && onConflict.equals("skip") && this.getAttemptCounter() > 0) {
                throw new RuntimeException("Ignore this node!");
            }

            this.incrementAttemptCounter();
            this.storageTreeExecute().removeStorageTreeNode(currentTarget);
            exitMessage = "File '%s' removed successfully!".formatted(currentTarget.getOnDiskURL());

            if (this.getAttemptCounter() > 1) {
                exitMessage.concat(" (Attempt %s)".formatted(this.getAttemptCounter().toString()));
            }

            this.setExitStatus(HttpStatus.OK);
            this.setExitMessage(exitMessage);
            this.logMessage(exitMessage);
        } catch (Exception exception) {
            if (onConflict != null && onConflict.equals("skip")) {
                exitMessage = "%s '%s' skipped!".formatted(currentTarget.getChildren() != null ? "Directory" : "File", currentTarget.getOnDiskURL());

                if (this.getAttemptCounter() > 1) {
                    exitMessage.concat(" (Attempt %s)".formatted(this.getAttemptCounter().toString()));
                }

                this.setExitStatus(HttpStatus.OK);
                this.setExitMessage(exitMessage);
                this.logMessage(exitMessage);
            } else {
                exitMessage = "File '%s' could not be removed: %s".formatted(currentTarget.getOnDiskURL(), exception.getMessage());
                this.setExitStatus(HttpStatus.UNPROCESSABLE_ENTITY);
                this.setExitMessage(exitMessage);
                this.logMessage(exitMessage);
                return; /* Failure!!! */
            }
        }

        this.resetAttemptCounter();
        this.setTaskAsDone();
    }
}
