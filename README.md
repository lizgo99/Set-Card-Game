# Set Card Game

## Overview

This project is a Java implementation of the classic Set Card Game, featuring a multithreaded architecture with concurrent gameplay. The game supports both human and bot players in a real-time competitive environment.

In the Set Card Game, players compete to identify "sets" of cards from those displayed on the table. A set consists of three cards where each feature (color, shape, number, and shading) is either all the same or all different across the three cards. Players must quickly identify valid sets and claim them before their opponents.

## Architecture & Design

This implementation follows an object-oriented design with a multithreaded architecture.  
The game is built around these key components, each running in its own thread:

- **Dealer**: Controls game flow, manages the timer, validates sets, and handles card placement/removal
- **Players**: Independent threads for both human and bot players that process inputs and identify sets
- **Table**: Thread-safe data structure maintaining the game state and card placements
- **UI**: Swing-based graphical interface providing real-time feedback

Thread synchronization is achieved through Java's concurrent programming features including synchronized blocks, wait/notify mechanisms, and thread-safe data structures to prevent race conditions.

### Design Patterns:

- **Observer Pattern**: For UI updates and game state changes
- **Factory Method**: For creating player instances
- **Decorator**: For enhancing the UI functionality

## Technical Highlights

- **Thread-safe Operations**: Carefully implemented synchronization to prevent race conditions
- **Dynamic UI**: Real-time updates reflecting game state changes
- **Configurable Game Parameters**: Customizable features through properties file
- **Logging System**: Comprehensive logging for debugging and monitoring
- **JUnit & Mockito Testing**: Thorough test coverage for core components

## Installation & Running

### Prerequisites

1. Java 8 or higher
2. Maven

### Setup and Run

1. Build the project:
```bash
mvn clean install
```

2. Run the game:
```bash
mvn exec:java -Dexec.mainClass="bguspl.set.Main"
```

Alternatively, after building, you can run the JAR file directly:
```bash
java -jar target/Set_Card_Game-1.0-SNAPSHOT.jar
```

## Configuration

The game behavior can be customized by modifying the `config.properties` file in the resources directory. Parameters include:

- Number of human/bot players
- Table size and layout
- Card deck configuration
- Game timing parameters
- UI properties

## Gameplay

- The dealer places 12 cards on the table
- Players search for sets among the displayed cards
- When a player identifies a set, they place tokens on the three cards
- The dealer validates the set
- If valid, the player receives a point, and the cards are replaced
- The game continues until the deck is exhausted and no more sets can be formed

## Tech Stack

- **Core**: Java 8 , Maven
- **UI**: Swing
- **Testing**: JUnit 5, Mockito
- **Performance**: Java Concurrency



