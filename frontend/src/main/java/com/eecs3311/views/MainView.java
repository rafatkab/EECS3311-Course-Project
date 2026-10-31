package com.eecs3311.views;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

@Route("")
@PageTitle("EECS3311 | Home")
public class MainView extends VerticalLayout {
    public MainView() {
        setSizeFull();
        setAlignItems(Alignment.CENTER);
        setJustifyContentMode(JustifyContentMode.CENTER);

        TextField name = new TextField("Your name");
        name.setMaxLength(100);
        Button greet = new Button("Say hello", event -> {
            String value = name.getValue().trim();
            Notification.show(value.isEmpty() ? "Welcome!" : "Hello, " + value + "!");
        });
        greet.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        add(new H1("EECS3311 Course Project"),
                new Paragraph("Welcome to your Vaadin Flow application."), name, greet);
    }
}
