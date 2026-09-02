import fs from 'node:fs/promises';
import { Workbook, SpreadsheetFile } from '@oai/artifact-tool';

const outputDir = 'C:/Users/zdrav/Desktop/Raid Alliance Logistics Liason Equipment/outputs/settings-description-review';
const outPath = `${outputDir}/ralle-settings-descriptions.xlsx`;
const previewPath = `${outputDir}/ralle-settings-descriptions-preview.png`;

const rows = [
  ['About','Interface','Choice','interface-font','Interface Font','Chooses Vanilla or Karla for RALLE interface body text and controls. Fox screen headers always use Karla.','Vanilla','Vanilla; Karla','', '', '', ''],
  ['Chat','General','Boolean','chat-enabled','Enable Chat Customization',"Allows RALLE's local chat options to operate in singleplayer or on any server.",'Off','On / Off','', '', '', ''],
  ['Chat','General','Action','edit-chat-layout','Edit Chat Layout','Opens the HUD layout editor for moving, resizing, or restoring the single chat box.','—','—','Chat enabled','', '', ''],
  ['Chat','Appearance','Boolean','hide-chat-scrollbar','Hide Chat Scrollbar',"Hides Minecraft's scrollbar while chat is open. Mouse-wheel scrolling still works.",'Off','On / Off','Chat enabled','', '', ''],
  ['Chat','Appearance','Boolean','message-direction-enabled','Custom Message Direction',"Enables RALLE's selected top-down or bottom-up message direction.",'Off','On / Off','Chat enabled','', '', ''],
  ['Chat','Appearance','Choice','message-direction','Message Direction','Chooses whether new messages appear from the bottom or the top.','Bottom-up','Bottom-up; Top-down','Chat enabled; Custom Message Direction','', '', ''],
  ['Chat','Appearance','Boolean','horizontal-alignment-enabled','Custom Horizontal Alignment',"Enables RALLE's selected left or right text alignment.",'Off','On / Off','Chat enabled','', '', ''],
  ['Chat','Appearance','Choice','horizontal-alignment','Horizontal Alignment','Chooses whether chat text is aligned to the left or right edge.','Left','Left; Right','Chat enabled; Custom Horizontal Alignment','', '', ''],
  ['Chat','Appearance','Boolean','text-shadow-enabled','Custom Text Shadow',"Enables RALLE's selected chat text shadow style.",'Off','On / Off','Chat enabled','', '', ''],
  ['Chat','Appearance','Choice','text-shadow','Text Shadow',"Chooses no shadow, Minecraft's vanilla shadow, an opaque offset shadow, or a soft shadow wrapped around the text.",'Vanilla','None; Vanilla; Partial-full; Full','Chat enabled; Custom Text Shadow','', '', ''],
  ['Chat','Screenshots','Boolean','chat-screenshot-enabled','Enable Chat Screenshotting','While chat is open, hold either Ctrl key and left-drag across messages to prepare a transparent chat image. Ctrl+C or double-click copies it.','Off','On / Off','Chat enabled','', '', ''],
  ['Chat','Screenshots','Boolean','chat-screenshot-smooth-expansion','Smooth Selection Expansion','Animates screenshot selection height changes over 120 milliseconds instead of snapping immediately.','Off','On / Off','Chat enabled; Enable Chat Screenshotting','', '', ''],
  ['Chat','Screenshots','Boolean','chat-selection-sounds','Chat Selection Sounds','Plays instrument feedback as the selected message count changes and after a chat image is copied successfully.','Off','On / Off','Chat enabled; Enable Chat Screenshotting','', '', ''],
  ['Raid LFG','General','Boolean','raid-lfg-enabled','Enable Raid LFG','Allows RALLE to authenticate and connect to the Kingdom Foxes Raid LFG service after joining Wynncraft.','Off','On / Off','', '', '', ''],
  ['Raid LFG','Notifications','Boolean','new-party-notifications','New Party Notifications','Shows a compact HUD card when a new party becomes available to join.','Off','On / Off','Raid LFG enabled','', '', ''],
  ['Raid LFG','Notifications','Boolean','reopened-party-notifications','Reopened Party Notifications','Shows a compact HUD card when a locked or unavailable party becomes joinable again.','Off','On / Off','Raid LFG enabled','', '', ''],
  ['Raid LFG','Notifications','Boolean','party-status-notifications','Party Status Notifications','Keeps your current party on the HUD when it was created or joined outside the Raid LFG screen. Close the card to dismiss it.','Off','On / Off','Raid LFG enabled','', '', ''],
  ['Raid LFG','Notifications','Boolean','auto-pop-out-main-ui','Auto Pop-out','After you create or join a party through Raid LFG, closes the screen and keeps that party on the HUD until you leave, disband, or close the card.','Off','On / Off','Raid LFG enabled','', '', ''],
  ['Raid LFG','Notifications','Boolean','notification-sounds','Raid LFG Sounds','Plays sound feedback for Raid LFG notifications and confirmed party membership changes.','Off','On / Off','Raid LFG enabled','', '', ''],
  ['Raid LFG','Notifications','Action','edit-notification-position','Edit Notification Position','Opens the HUD editor with the fixed-size Raid LFG notification anchor selected.','—','—','Raid LFG enabled','', '', ''],
  ['Raid LFG','Controls','Keybind','raid-lfg-keybind','Raid LFG Keybind','Opens Raid LFG with a configurable key. It is unbound by default; /ralle lfg is always available as a fallback.','Unbound','Any key; Unbound','Raid LFG enabled','', '', ''],
  ['Raid LFG','Controls','Keybind','raid-lfg-join-keybind','Join Keybind','Starts the three-second Join countdown for the newest visible joinable notification card.','Unbound','Any key; Unbound','Raid LFG enabled','', '', ''],
  ['Raid LFG','Controls','Keybind','raid-lfg-close-keybind','Close Notification Keybind','Dismisses the newest visible Raid LFG notification without cancelling or leaving anything.','Unbound','Any key; Unbound','Raid LFG enabled','', '', ''],
  ['Raid LFG','Controls','Keybind','raid-lfg-leave-disband-keybind','Leave / Disband Keybind','Leaves your current party, or arms a five-second second-press confirmation when you are host.','Unbound','Any key; Unbound','Raid LFG enabled','', '', ''],
  ['Raid LFG','Controls','Keybind','raid-lfg-party-filled-keybind','Party Filled Keybind','Invites every synchronized non-host member to the Wynncraft party when your Raid LFG lobby is full.','Unbound','Any key; Unbound','Raid LFG enabled','', '', ''],
  ['Raid LFG','Controls','Keybind','raid-lfg-ping-keybind','Ping Keybind','Pings the current party when you are its host and Ping is off cooldown.','Unbound','Any key; Unbound','Raid LFG enabled','', '', ''],
  ['Raid LFG','Controls','Keybind','raid-lfg-lock-keybind','Lock / Unlock Keybind','Toggles joining for the current party after a one-second coalescing delay when you are its host.','Unbound','Any key; Unbound','Raid LFG enabled','', '', ''],
  ['Raid LFG','Controls','Keybind','raid-lfg-create-keybind','Create Modifier Keybind','Hold this key and release top-row 1–6 to create Dailies, NOTG, NOL, TCC, TNA, or TWP.','Unbound','Any key; Unbound','Raid LFG enabled','', '', ''],
  ['Raid LFG','Controls','Keybind','raid-lfg-kick-keybind','Kick Modifier Keybind','Hold this key and release top-row 2–4 to kick that synchronized roster slot.','Unbound','Any key; Unbound','Raid LFG enabled','', '', ''],
];

const workbook = Workbook.create();
const sheet = workbook.worksheets.add('Settings');
sheet.showGridLines = false;
sheet.getRange('A1:L1').merge();
sheet.getRange('A1').values = [['RALLE Settings Description Review']];
sheet.getRange('A2:L2').merge();
sheet.getRange('A2').values = [['Edit Proposed Description and Notes. Leave Proposed Description blank when no wording change is needed. Status updates automatically.']];
sheet.getRange('A4:L4').values = [['Category','Subcategory','Entry Type','Setting ID','Title','Current Description','Default / Value','Options','Required Parent','Proposed Description','Notes','Status']];
sheet.getRange(`A5:L${rows.length + 4}`).values = rows;
sheet.getRange(`L5`).formulas = [[`=IF(AND(J5="",K5=""),"Unchanged",IF(J5<>"","Description draft","Needs clarification"))`]];
sheet.getRange(`L5:L${rows.length + 4}`).fillDown();

const title = sheet.getRange('A1:L1');
title.format = { fill: '#14233D', font: { bold: true, color: '#FFFFFF', size: 16 }, horizontalAlignment: 'left', verticalAlignment: 'center' };
title.format.rowHeight = 30;
sheet.getRange('A2:L2').format = { fill: '#EAF0F8', font: { color: '#334155', italic: true, size: 10 }, wrapText: true, verticalAlignment: 'center' };
sheet.getRange('A2:L2').format.rowHeight = 28;
sheet.getRange('A4:L4').format = { fill: '#D98B32', font: { bold: true, color: '#FFFFFF' }, wrapText: true, verticalAlignment: 'center', borders: { preset: 'outside', style: 'medium', color: '#9A5C1A' } };
sheet.getRange('A4:L4').format.rowHeight = 30;
sheet.getRange(`A5:L${rows.length + 4}`).format = { verticalAlignment: 'top', wrapText: true, borders: { insideHorizontal: { style: 'thin', color: '#D7DEE9' } } };
sheet.getRange(`J5:K${rows.length + 4}`).format = { fill: '#FFF7D6', verticalAlignment: 'top', wrapText: true, borders: { insideHorizontal: { style: 'thin', color: '#D7DEE9' } } };
sheet.getRange(`L5:L${rows.length + 4}`).format = { fill: '#F1F5F9', horizontalAlignment: 'center', verticalAlignment: 'top', wrapText: true };
sheet.getRange(`A5:A${rows.length + 4}`).format.font = { bold: true, color: '#1E3A5F' };
sheet.getRange(`D5:D${rows.length + 4}`).format.font = { color: '#475569' };
sheet.getRange(`F5:F${rows.length + 4}`).format.font = { color: '#334155' };
sheet.getRange('A:L').format.font = { name: 'Aptos', size: 10 };
title.format.font = { name: 'Aptos Display', bold: true, color: '#FFFFFF', size: 16 };
sheet.getRange('A4:L4').format.font = { name: 'Aptos', bold: true, color: '#FFFFFF', size: 10 };
const widths = { A: 15, B: 18, C: 12, D: 30, E: 28, F: 68, G: 16, H: 26, I: 34, J: 68, K: 42, L: 20 };
for (const [col, width] of Object.entries(widths)) sheet.getRange(`${col}:${col}`).format.columnWidth = width;
sheet.getRange(`A5:L${rows.length + 4}`).format.rowHeight = 42;
sheet.freezePanes.freezeRows(4);
sheet.getRange('A4:L34').format.autofitRows();

const notes = workbook.worksheets.add('Notes');
notes.showGridLines = false;
notes.getRange('A1:D1').merge();
notes.getRange('A1').values = [['Clarifications and follow-up notes']];
notes.getRange('A2:D2').merge();
notes.getRange('A2').values = [['Use this sheet for broader decisions, questions, or changes that apply to multiple settings.']];
notes.getRange('A4:D4').values = [['Date','Setting ID or Topic','Note / Clarification','Decision / Follow-up']];
notes.getRange('A5:D14').values = Array.from({ length: 10 }, () => ['', '', '', '']);
notes.getRange('A1:D1').format = { fill: '#14233D', font: { name: 'Aptos Display', bold: true, color: '#FFFFFF', size: 16 }, verticalAlignment: 'center' };
notes.getRange('A1:D1').format.rowHeight = 30;
notes.getRange('A2:D2').format = { fill: '#EAF0F8', font: { name: 'Aptos', color: '#334155', italic: true }, wrapText: true };
notes.getRange('A4:D4').format = { fill: '#D98B32', font: { name: 'Aptos', bold: true, color: '#FFFFFF' }, borders: { preset: 'outside', style: 'medium', color: '#9A5C1A' } };
notes.getRange('A5:D14').format = { fill: '#FFF7D6', wrapText: true, verticalAlignment: 'top', borders: { insideHorizontal: { style: 'thin', color: '#D7DEE9' } }, rowHeight: 42 };
notes.getRange('A:A').format.columnWidth = 15;
notes.getRange('B:B').format.columnWidth = 32;
notes.getRange('C:C').format.columnWidth = 72;
notes.getRange('D:D').format.columnWidth = 42;
notes.freezePanes.freezeRows(4);

await fs.mkdir(outputDir, { recursive: true });
const preview = await workbook.render({ sheetName: 'Settings', range: 'A1:L12', scale: 1, format: 'png' });
await fs.writeFile(previewPath, new Uint8Array(await preview.arrayBuffer()));
const exported = await SpreadsheetFile.exportXlsx(workbook);
await exported.save(outPath);

const check = await workbook.inspect({ kind: 'table', range: 'Settings!A1:L10', include: 'values,formulas', tableMaxRows: 10, tableMaxCols: 12, maxChars: 5000 });
console.log(check.ndjson);
const errors = await workbook.inspect({ kind: 'match', searchTerm: '#REF!|#DIV/0!|#VALUE!|#NAME\\?|#N/A', options: { useRegex: true, maxResults: 50 }, summary: 'formula errors' });
console.log(errors.ndjson);
console.log(`Saved ${outPath}`);
