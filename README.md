# Android Group Messenger

### Part A: Group Messenger with a Local Persistent Key-Value Table

Design a group messenger that can send message to multiple AVDs and store them in a permanent key-value storage.

* Write a content provider which should be used to store all messages, but the abstraction it provides should be a general key-value table. 
* After implementing your provider, verify whether or not you the requirements are met by clicking “PTest” provided in the template.
* Implement multicast, i.e., sending messages to multiple AVDs.
* The app needs to assign a sequence number to each message it receives. The sequence number should start from 0 and increase by 1 for each message.
* Each message and its sequence number should be stored as a <key, value> pair in the content provider. The key should be the sequence number for the message (as a string); the value should be the actual message (again, as a string).

### Part B: Group Messenger with Total and FIFO Ordering Guarantees and Failure Detection
