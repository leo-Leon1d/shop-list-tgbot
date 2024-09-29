package com.example.demo;

import lombok.SneakyThrows;
import org.telegram.telegrambots.bots.DefaultBotOptions;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.commands.SetMyCommands;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.commands.BotCommand;
import org.telegram.telegrambots.meta.api.objects.commands.scope.BotCommandScopeDefault;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TelegramBot extends TelegramLongPollingBot {

    private final Map<Long, String> userState = new HashMap<>();
    private final Map<Long, String> userItem = new HashMap<>();

    public TelegramBot(DefaultBotOptions options, String botToken) {
        super(options, botToken);
        registerBotCommands();
    }
    @Override
    @SneakyThrows
    public void onUpdateReceived(Update update) {
        if(update.hasCallbackQuery()) {
            AnswerCallbackQuery answerCallbackQuery = new AnswerCallbackQuery(update.getCallbackQuery().getId());
            answerCallbackQuery.setText("Обработка...");
            execute(answerCallbackQuery);

            String callbackData = update.getCallbackQuery().getData();
            Long chatId = update.getCallbackQuery().getMessage().getChatId();

            if(callbackData.startsWith("removeAll")) {
                Connection conn = DatabaseConnection.getConn();
                String query = "DELETE FROM shopping";
                PreparedStatement statement = conn.prepareStatement(query);
                statement.executeUpdate();

                SendMessage sendMessage = new SendMessage(chatId.toString(),
                        "Все товары были успешно удалены.");
                sendApiMethod(sendMessage);
            }

            if(callbackData.startsWith("remove_")) {
                String itemId = callbackData.substring("remove_".length());

                Connection conn = DatabaseConnection.getConn();
                String query = "DELETE FROM shopping WHERE id = ?";
                PreparedStatement statement = conn.prepareStatement(query);
                statement.setInt(1, Integer.parseInt(itemId));
                statement.executeUpdate();

                SendMessage sendMessage = new SendMessage(chatId.toString(),
                        "Товар успешно удален.");
                sendApiMethod(sendMessage);
            }
        }
        if(update.hasMessage()) {
            Long chatId = update.getMessage().getChatId();
            if(!update.getMessage().hasText()) {
                System.out.println("NO TEXT");
                SendMessage sendMessage = new SendMessage(chatId.toString(),
                        "Пожалуйста, напишите текстовое сообщение.");
                sendApiMethod(sendMessage);
            } else {
                String text = update.getMessage().getText();
                if (text.startsWith("/")) {
                    text = text.split("@")[0];
                }
                System.out.println(text);

                if (userState.containsKey(chatId) && userState.get(chatId).equals("waiting_for_amount")) {
                    handleAddAmount(chatId, text);
                } else if (userState.containsKey(chatId) && userState.get(chatId).equals("waiting_for_item")) {
                    handleAddItem(chatId, text);
                } else if (text.equals("/add")) {
                    handleAdd(chatId);
                } else if (text.equals("/list")) {
                    handleList(chatId);
                } else if (text.equals("/remove")) {
                    handleRemove(chatId);
                } else {
                    SendMessage sendMessage = new SendMessage(chatId.toString(),
                            "Пожалуйста, воспользуйтесь одной из команд бота.");
                    sendApiMethod(sendMessage);
                }
            }
        }
    }

    @SneakyThrows
    public void handleAdd(Long chatId) {
        SendMessage sendMessage = new SendMessage(chatId.toString(),
                "Введите название продукта:");
        sendApiMethod(sendMessage);
        userState.put(chatId, "waiting_for_item");
    }

    @SneakyThrows
    public void handleAddItem(Long chatId, String itemName) {
        userItem.put(chatId, itemName);
        SendMessage sendMessage = new SendMessage(chatId.toString(),
                "Введите количество для \"" + itemName + "\":");
        sendApiMethod(sendMessage);
        userState.put(chatId, "waiting_for_amount");
    }

    @SneakyThrows
    public void handleAddAmount(Long chatId, String amountText) {
        try {
            int amount = Integer.parseInt(amountText);
            String itemName = userItem.get(chatId);

            if (itemName != null) {

                Connection conn = DatabaseConnection.getConn();
                String query = "INSERT INTO shopping (name, amount) VALUES (?, ?)";
                PreparedStatement statement = conn.prepareStatement(query);
                statement.setString(1, itemName);
                statement.setInt(2, amount);
                statement.executeUpdate();

                SendMessage sendMessage = new SendMessage(chatId.toString(),
                        "Товар \"" + itemName + "\" в количестве " + amount + " добавлен в список.");
                sendApiMethod(sendMessage);
            }

            userState.remove(chatId);
            userItem.remove(chatId);

        } catch (NumberFormatException e) {
            SendMessage sendMessage = new SendMessage(chatId.toString(),
                    "Пожалуйста, введите корректное количество (число).");
            sendApiMethod(sendMessage);
        }
    }
    @SneakyThrows
    public void handleList(Long chatId) {
        Connection conn = DatabaseConnection.getConn();
        String query = "SELECT name, amount FROM shopping";
        PreparedStatement statement = conn.prepareStatement(query);
        ResultSet resultSet = statement.executeQuery();

        StringBuilder shoppingList = new StringBuilder("Список покупок:\n");
        boolean hasItems = false;

        while (resultSet.next()) {
            hasItems = true;

            if (resultSet.getInt("amount") > 1) {
                shoppingList
                        .append(resultSet.getString("name"))
                        .append(" (")
                        .append(resultSet.getInt("amount"))
                        .append(")\n");
            } else {
                shoppingList.append(resultSet.getString("name")).append("\n");
            }
        }

        if (!hasItems) {
            SendMessage sendMessage = new SendMessage(chatId.toString(),
                    "Ваш список пуст.");
            sendApiMethod(sendMessage);
        } else {
            SendMessage message = new SendMessage(chatId.toString(), shoppingList.toString());
            sendApiMethod(message);
        }
    }

    public void registerBotCommands () {
        List<BotCommand> botCommands = new ArrayList<>();
        botCommands.add(new BotCommand("/list",
                "Просмотреть список покупок"));
        botCommands.add(new BotCommand("/add",
                "Добавить в список покупок"));
        botCommands.add(new BotCommand("/remove",
                "Убрать из списка покупок"));
        try {
            this.execute(new SetMyCommands(botCommands, new BotCommandScopeDefault(), null));
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    @SneakyThrows
    public void handleRemove(Long chatId) {
        Connection conn = DatabaseConnection.getConn();
        String query = "SELECT id, name, amount FROM shopping";
        PreparedStatement statement = conn.prepareStatement(query);
        ResultSet resultSet = statement.executeQuery();

        if (!resultSet.isBeforeFirst()) {
            SendMessage sendMessage = new SendMessage(chatId.toString(), "Ваш список пуст.");
            sendApiMethod(sendMessage);
        } else {
            removePick(chatId, resultSet);
        }
    }


    @SneakyThrows
    public void removePick(Long chatId, ResultSet resultSet) {
        SendMessage sendMessage = new SendMessage(chatId.toString(),
                "Выберите предмет для удаления из списка:");

        InlineKeyboardMarkup inlineKeyboardMarkup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        while (resultSet.next()) {
            List<InlineKeyboardButton> row = new ArrayList<>();
            String itemId = String.valueOf(resultSet.getInt("id"));
            String itemName = resultSet.getString("name");

            row.add(InlineKeyboardButton.builder()
                    .text(itemName)
                    .callbackData("remove_" + itemId)
                    .build());
            rows.add(row);
        }
        List<InlineKeyboardButton> row = new ArrayList<>();
        row.add(InlineKeyboardButton.builder()
                .text("Удалить всё")
                .callbackData("removeAll")
                .build());
        rows.add(row);
        inlineKeyboardMarkup.setKeyboard(rows);
        sendMessage.setReplyMarkup(inlineKeyboardMarkup);
        sendApiMethod(sendMessage);
    }


    @Override
    public String getBotUsername() {
        return "Shopping Assistant";
    }
}
