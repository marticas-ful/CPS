# Concurrent and Parallel Systems Coursework
My coursework for Concurrent and Parallel Systems
Programming languages: Java, SQL
Additional resources: SQLite Database "LUConnect.db", Sound files "incoming_message.wav", and "outgoing_message.wav"

A brief overview of classes:
1. LUConnectServer:
   This is the server of the application. Key functions are: accepting and authenticating users, routing messages between users (including file transfers), maintaining a list      of active users that is updated whenever a user leaves, and managing waitlisted users through inner classes 'SimpleSemaphore' and 'WaitingClient'.
2. ClientHandler:
   This class manages individual client connections in the server class. Key functions are: connecting each user through their own ClientHandler instance, managing incoming        messages (regular and file), maintaining socket connection with the client, and sending messages directly to the client.
3. LUConnectClient:
   This is the client class of the application. Key functions are: providing UI for login, registration, waitlist, and chat screens, connecting to the server and authenticating    users, sound notifications when messages are sent, and storing messages with encryption in DB.
4. DBConnection:
   This is the dedicated Database connection class. Key functions are: creating one instance of the database to be used across the program, connecting to "LUConnect.db" in the     resources folder, authenticating and registering users in the database, as well as storing messaging history in the database.
5. Security:
   This class handles encryption and decryption of data in the database. Key functions are: AES encryption turning plain text into Base-64 encoded text, and AES decryption,       which does the opposite
6. NotificationTone:
   This class is a helper class for 'LUConnectClient' which plays specified audio.
   
Notice: If there exist any inconsistencies between the uploaded changes, it is due to the fact that the github was created after the final program. Hence, what is seen is a somewhat accurate reconstruction of how I went about creating the program from a simple multi-threaded messanger into incorporating several functionalities.
