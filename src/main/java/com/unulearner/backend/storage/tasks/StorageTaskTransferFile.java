package com.unulearner.backend.storage.tasks;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;

import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;

import com.unulearner.backend.storage.StorageTree;
import com.unulearner.backend.storage.StorageTreeNode;

public class StorageTaskTransferFile extends StorageTask {
    private final Boolean persistOriginal;

    public StorageTaskTransferFile(@NonNull StorageTree storageTree, @NonNull StorageTreeNode targetNode, @NonNull StorageTreeNode destinationNode, Boolean persistOriginal) {
        super(storageTree, targetNode);

        this.setCurrentDestination(destinationNode);
        this.setCurrentTarget(targetNode);
        this.setOnConflictOptions(new ArrayList<>(Arrays.asList(
            new Option("overwrite", "Overwrite", true, false),
            new Option("rename", "Rename", true, true),
            new Option("merge", "Merge", false, true),
            new Option("skip", "Skip", true, true)
        )));

        this.persistOriginal = persistOriginal;
        final String exitMessage = "File transfer task initiated on %s!".formatted(Instant.now().toString());
        this.setExitStatus(HttpStatus.ACCEPTED);
        this.setExitMessage(exitMessage);
        this.logMessage(exitMessage);
    }

    @Override
    public synchronized void run(String onConflictAction, Boolean onConflictActionIsPersistent, Boolean cancelTaskExecution) {
        String exitMessage = null;

        if (cancelTaskExecution != null && cancelTaskExecution == true) {
            exitMessage = "File transfer task cancelled on %s!".formatted(Instant.now().toString());
            this.setExitStatus(HttpStatus.OK);
            this.setExitMessage(exitMessage);
            this.logMessage(exitMessage);
            this.setTaskAsDone();
            return;
        }
        
        //TODO: this is an error condition!
        final StorageTreeNode currentTarget = this.getCurrentTarget();
        if (currentTarget == null) {
            exitMessage = "File transfer task finished successfully on %s!".formatted(Instant.now().toString());
            this.setExitStatus(HttpStatus.OK);
            this.setExitMessage(exitMessage);
            this.logMessage(exitMessage);
            this.setTaskAsDone();
            return;
        }

        //TODO: this is an error condition!
        final StorageTreeNode currentDestination = this.getCurrentDestination();
        if (currentDestination == null) {
            exitMessage = "WTF? no destination? on %s!".formatted(Instant.now().toString());
            this.setExitStatus(HttpStatus.EXPECTATION_FAILED);
            this.setExitMessage(exitMessage);
            this.logMessage(exitMessage);
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
            this.storageTreeExecute().commitStorageTreeNode(currentTarget, currentDestination, this.persistOriginal, onConflict);
            exitMessage = "File '%s' transfered to '%s' successfully!".formatted(currentTarget.getOnDiskURL(), currentDestination.getOnDiskURL());

            if (this.getAttemptCounter() > 1) {
                exitMessage.concat(" (Attempt %s)".formatted(this.getAttemptCounter().toString()));
            }

            this.setExitStatus(HttpStatus.OK);
            this.setExitMessage(exitMessage);
            this.logMessage(exitMessage);
        } catch (Exception exception) {
            if (onConflict != null && onConflict.equals("skip")) {
                exitMessage = "File '%s' transfer to '%s' directory skipped!".formatted(currentTarget.getOnDiskURL(), currentDestination.getOnDiskURL());

                if (this.getAttemptCounter() > 1) {
                    exitMessage.concat(" (Attempt %s)".formatted(this.getAttemptCounter().toString()));
                }

                this.setExitStatus(HttpStatus.OK);
                this.setExitMessage(exitMessage);
                this.logMessage(exitMessage);
            } else {
                exitMessage = "File '%s' could not be transfered to '%s' directory: %s".formatted(currentDestination.getOnDiskURL(), exception.getMessage());
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
