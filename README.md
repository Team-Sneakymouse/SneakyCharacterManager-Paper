# SneakyCharacterManager-Paper
 Paper plugin for switching characters in DvZ and LoM

## BungeeCord End:
1. Stores player character information in unique .yml files PLAYERUUID.yml
   2. General format for character information:
   ```yaml
    last-used-character: "<charuuid>"
    characters:
      <charuuid>: #Randomly Generated UUID
        name: "Jimmy 3 Fingers"
        skin: "HTTP Link to Skin || File Path To Skin"
      <charuuid2>:
        name: "Rasputin Fanghorn"
        skin: "HTTP Link to Skin || File Path To Skin"
      #File Path for Skins are a 50/50 depending on impl & will need to be managed on the Backend side not Bungee
   ```
   
## Backend Servers:
1. Character Data Storage:
   1. Store character information in a .yml format containing:
      1. Last "Logout" Information (Location)
      2. Character Inventory
   2. Function to wipe character data as needed


2. When players switch characters they will spawn where they last were as that character & with what ever items that character has


3. Character Selection menu containing player heads with the character skins & name
   1. Possibly also details like Last Played date and, in the case of LoM, last district?


4. Character Creation Menu
   1. Can set character name, and skin. Data is sent to the Bungee Server for storage
   2. "Quick Create" only works 1 time. Creates a character with players base username & skin data


5. Allow the deletion of characters BUT players must keep minimum 1 character


6. Limited Character Slots:
   1. Will require a fair bit of communication between Bungee & Backend
   2. Players can only have a limited character count to avoid over use & to learn how the RPer plays
   3. Will have an 'unlimited' permission node to bypass character limit
   4. LoM has a skill tree on Character Slots, might need to interface with it and store slots as a number?
   5. Royals most likely get Unlimited by default :)

We will need to be able to quickly send request between Bungee & Backend servers to fetch character details, and character slots.
Will avoid fetching as much as possible to limit server lag & delay. Should only need to request for the following events:
```
1. On Player Join A Backend Server
   - Fetch last used character & load it to the player
2. On Character Selection Menu Opened
   - Fetch all characters available to the player
3. On Character Creation Completed
   - Send new character data to Bungee
4. On Disconnect
   - Store last used character on Bungee 
```


**Note: Most of this is SUBJECT TO CHANGE as requirements are adjusted!**