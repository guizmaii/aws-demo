package io.simplesource.example.demo.repository.write.simplesource;

import io.simplesource.api.CommandAPI;
import io.simplesource.api.CommandError;
import io.simplesource.api.CommandId;
import io.simplesource.data.FutureResult;
import io.simplesource.data.Result;
import io.simplesource.data.Sequence;
import io.simplesource.example.demo.repository.write.AccountWriteRepository;
import io.simplesource.example.demo.repository.write.CreateAccountError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.util.Optional;

/**
 * Use Simplesourcing as the write store
 */
public class SimplesourceAccountWriteRepository implements AccountWriteRepository {
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(60);
    private static final Logger log = LoggerFactory.getLogger(SimplesourceAccountWriteRepository.class);

    private CommandAPI<String, AccountCommand> commandApi;

    public SimplesourceAccountWriteRepository(@Autowired CommandAPI<String, AccountCommand> commandApi) {
        this.commandApi = commandApi;
    }

    @Override
    public Optional<CreateAccountError> create(String accountName, double openingBalance) {
        FutureResult<CommandError, Sequence> result = commandApi.publishAndQueryCommand(new CommandAPI.Request<>(CommandId.random(), accountName, Sequence.first(), new AccountCommand.CreateAccount(accountName, openingBalance)), DEFAULT_TIMEOUT);

        //TODO handle future resolution and error handling properly, below is a quick hacky just do it implementation
        final Result<CommandError, Sequence> resolved = result.unsafePerform(CommandError.CommandHandlerFailed::new);


        if (resolved.failureReasons().isPresent()) {
            CommandError head = resolved.failureReasons().get().head();

            if (head instanceof CommandError.InvalidReadSequence) {
                return Optional.of(new CreateAccountError.AccountAlreadyExists());
            } else if (head instanceof CreateAccountError) {
                return Optional.of((CreateAccountError) head);
            } else {
                throw head;
            }
        }

        return Optional.empty();
    }

    @Override
    public void deposit(String account, double amount, Sequence version) {
        FutureResult<CommandError, Sequence> result = commandApi.publishAndQueryCommand(new CommandAPI.Request<>(CommandId.random(), account, version, new AccountCommand.Deposit(amount)), DEFAULT_TIMEOUT);

        Result<CommandError, Sequence> commandErrorSequenceResult = result.unsafePerform(CommandError.InternalError::new);

        commandErrorSequenceResult.failureReasons()
                .map(errors -> (Runnable) () -> {
                    log.info("Failed depositing {} in account {} with seq {}", amount, account, version);
                    errors.forEach(error -> {
                        log.error("  - {}", error.getMessage());
                    });
                    throw new RuntimeException("Deposit failed"); // TODO should return a value
                })
                .orElse(() -> {})
                .run();

    }

    @Override
    public void withdraw(String account, double amount, Sequence position) {
        FutureResult<CommandError, Sequence> result = commandApi.publishAndQueryCommand(new CommandAPI.Request<>(CommandId.random(), account, position, new AccountCommand.Withdraw(amount)), DEFAULT_TIMEOUT);

        Result<CommandError, Sequence> commandErrorSequenceResult = result.unsafePerform(CommandError.InternalError::new);

        commandErrorSequenceResult.failureReasons()
                .map(errors -> (Runnable) () -> {
                    log.info("Failed depositing {} in account {} with seq {}", amount, account, position.getSeq());
                    errors.forEach(error -> {
                        log.error("  - {}", error.getMessage());
                    });
                    throw new RuntimeException("Withdraw failed"); // TODO should return a value
                })
                .orElse(() -> {})
                .run();
    }
}
