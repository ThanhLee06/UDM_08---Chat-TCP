package vn.edu.ut.udm08.client.controller;
import java.util.function.Consumer;
import java.util.concurrent.Callable;
import javafx.concurrent.Task;
public final class AccountTaskRunner {
    private AccountTaskRunner() {
    }
    public static <T> void run(Callable<T> work,
                              Consumer<T> success,
                              Consumer<String> failure) {
        Task<T> task = new Task<>() {
            @Override
            protected T call() throws Exception {
                return work.call();
            }
        };
        task.setOnSucceeded(event -> success.accept(task.getValue()));
        task.setOnFailed(event -> {
            Throwable cause = task.getException();
            failure.accept(cause instanceof IllegalStateException ? cause.getMessage()
                    : "Không thể xử lý yêu cầu. Vui lòng thử lại");
        });
        Thread worker = new Thread(task, "account-request");
        worker.setDaemon(true);
        worker.start();
    }
}
