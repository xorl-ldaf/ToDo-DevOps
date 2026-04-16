package com.example.todo.config;

import com.example.todo.application.port.out.PublishReminderEventPort;
import com.example.todo.domain.reminder.Reminder;

public final class NoOpReminderEventPublisher implements PublishReminderEventPort {

    @Override
    public void publish(Reminder reminder) {
        // Null Object:
        // пока внешнего publisher (Telegram / queue / email) нет,
        // приложение работает на явной безопасной заглушке,
        // а не на анонимной лямбде внутри BeanConfig.
    }
}