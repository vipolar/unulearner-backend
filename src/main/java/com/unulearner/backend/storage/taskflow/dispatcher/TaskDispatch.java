package com.unulearner.backend.storage.taskflow.dispatcher;

import java.util.Map;
import java.util.UUID;
import java.time.Instant;
import java.util.concurrent.*;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import com.unulearner.backend.storage.taskflow.Base;
import com.unulearner.backend.storage.taskflow.Create;
import com.unulearner.backend.storage.taskflow.Delete;
import com.unulearner.backend.storage.taskflow.Modify;
import com.unulearner.backend.storage.taskflow.ModifyField;
import com.unulearner.backend.storage.taskflow.ModifyOwnership;
import com.unulearner.backend.storage.taskflow.ModifyPermissions;
import com.unulearner.backend.storage.taskflow.Transfer;

import com.unulearner.backend.storage.config.StorageProperties;
import com.unulearner.backend.storage.taskflow.constant.TaskState;
import com.unulearner.backend.storage.exceptions.StorageUtilException;

@Service
public class TaskDispatch {
    private final Map<Class<?>, ObjectProvider<?>> taskFactoriesMap;
    private final Map<UUID, ScheduledFuture<?>> taskRemovalHashMap;
    private final ScheduledExecutorService taskRemovalScheduler;
    private final StorageProperties storageProperties;
    private final Map<UUID, Base> taskHashMap;

    private final Integer taskTimeOutInSeconds;
    private final Integer taskTimeOutGracePeriodInSeconds;
    private final Integer taskTimeOutWithGracePeriodIncludedInSeconds;

    public TaskDispatch(StorageProperties storageProperties,
            ObjectProvider<Create> createFactory,
            ObjectProvider<Delete> deleteFactory,
            ObjectProvider<Modify> modifyFactory,
            ObjectProvider<ModifyField> modifyFieldFactory,
            ObjectProvider<ModifyOwnership> modifyOwnershipFactory,
            ObjectProvider<ModifyPermissions> modifyPermissionsFactory, 
            ObjectProvider<Transfer> transferFactory) {
        this.taskRemovalScheduler = Executors.newScheduledThreadPool(1);
        this.taskRemovalHashMap = new ConcurrentHashMap<>();
        this.taskHashMap = new ConcurrentHashMap<>();
        this.storageProperties = storageProperties;

        this.taskFactoriesMap = Map.of(
            Create.class, createFactory,
            Delete.class, deleteFactory,
            Modify.class, modifyFactory,
            ModifyField.class, modifyFieldFactory,
            ModifyOwnership.class, modifyOwnershipFactory,
            ModifyPermissions.class, modifyPermissionsFactory,
            Transfer.class, transferFactory
        );

        this.taskTimeOutInSeconds = this.storageProperties.getTaskTimeOutInSeconds();
        this.taskTimeOutGracePeriodInSeconds = this.storageProperties.getTaskTimeOutGracePeriodInSeconds();
        this.taskTimeOutWithGracePeriodIncludedInSeconds = this.taskTimeOutInSeconds + this.taskTimeOutGracePeriodInSeconds;
    }

    @SuppressWarnings("unchecked")
    public <T> T createTask(Class<T> taskType) throws Exception {
        ObjectProvider<?> provider = taskFactoriesMap.get(taskType);
        T uncastTask = (T) provider.getObject();
        UUID taskUUID = null;

        do {
            taskUUID = UUID.randomUUID();
        } while (this.taskHashMap.get(taskUUID) != null || this.taskRemovalHashMap.get(taskUUID) != null);

        this.scheduleTaskRemoval(((Base) uncastTask).initialize(taskUUID));
        this.taskHashMap.put(taskUUID, (Base) uncastTask);
        return uncastTask;
    }

    public Base retrieveTask(UUID taskUUID) throws Exception {
        final ScheduledFuture<?> scheduledFuture = this.taskRemovalHashMap.get(taskUUID);
        final Base task = this.taskHashMap.get(taskUUID);

        if (task == null) {
            throw new StorageUtilException("Task UUID '%s' is invalid!".formatted(taskUUID.toString()));
        }

        if (scheduledFuture != null) {
            scheduledFuture.cancel(false);
        }
        
        task.getCompletionSignal().thenRun(() -> {
            if (!(task.getCurrentState() == TaskState.COMPLETED || task.getCurrentState() == TaskState.CANCELLED)) {
                this.scheduleTaskRemoval(task);
            } else {
                this.removeTask(task);
            }
        });

        return task;
    }

    private void scheduleTaskRemoval(Base task) {
        final ScheduledFuture<?> scheduledFuture = this.taskRemovalHashMap.get(task.getTaskUUID());

        if (scheduledFuture != null) {
            scheduledFuture.cancel(false);
        }

        if (this.taskTimeOutWithGracePeriodIncludedInSeconds == 0) {
            return;
        }

        final ScheduledFuture<?> newScheduledFuture = this.taskRemovalScheduler.schedule(() -> {
            this.taskRemovalHashMap.remove(task.getTaskUUID());
            this.taskHashMap.remove(task.getTaskUUID());
        }, this.taskTimeOutWithGracePeriodIncludedInSeconds, TimeUnit.SECONDS);

        task.getCurrentAction().setValidBefore(Instant.now().toEpochMilli() + (this.taskTimeOutInSeconds * 1000));
        this.taskRemovalHashMap.put(task.getTaskUUID(), newScheduledFuture);
    }

    private void removeTask(Base task) {
        final ScheduledFuture<?> scheduledFuture = this.taskRemovalHashMap.get(task.getTaskUUID());

        if (scheduledFuture != null) {
            scheduledFuture.cancel(false);
        }

        this.taskRemovalHashMap.remove(task.getTaskUUID());
        this.taskHashMap.remove(task.getTaskUUID());
    }
}
