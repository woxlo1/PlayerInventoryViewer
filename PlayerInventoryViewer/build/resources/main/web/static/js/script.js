// Player Inventory Viewer JavaScript

let currentPlayers = [];
let selectedPlayer = null;

// ページ読み込み時の初期化
document.addEventListener('DOMContentLoaded', function() {
    loadPlayers();

    // 検索機能
    document.getElementById('playerSearch').addEventListener('input', filterPlayers);
});

// プレイヤー一覧を読み込み
async function loadPlayers() {
    try {
        showLoading('プレイヤー一覧を読み込み中...');

        const response = await fetch('/api/players');
        if (!response.ok) {
            throw new Error(`HTTP error! status: ${response.status}`);
        }

        const players = await response.json();
        currentPlayers = players;
        displayPlayers(players);

    } catch (error) {
        console.error('プレイヤー一覧の読み込みに失敗:', error);
        showError('プレイヤー一覧の読み込みに失敗しました。サーバーが起動しているか確認してください。');
    }
}

// プレイヤー一覧を表示
function displayPlayers(players) {
    const playerList = document.getElementById('playerList');

    if (players.length === 0) {
        playerList.innerHTML = '<p class="loading">プレイヤーが見つかりません</p>';
        return;
    }

    const html = players.map(player => `
        <div class="player-card ${player.online ? 'online' : 'offline'}" 
             onclick="selectPlayer('${player.name}')">
            <div class="player-name">${player.name}</div>
            <div class="player-status">
                ${player.online ? 
                    `🟢　オンライン (${player.world} ${Math.round(player.x)}, ${Math.round(player.y)}, ${Math.round(player.z)})` :
                    `🔴 オフライン (最終ログイン: ${formatDate(player.lastSeen)})`
                }
            </div>
        </div>
    `).join('');

    playerList.innerHTML = html;
}

// プレイヤーのフィルタリング
function filterPlayers() {
    const searchTerm = document.getElementById('playerSearch').value.toLowerCase();
    const filteredPlayers = currentPlayers.filter(player => 
        player.name.toLowerCase().includes(searchTerm)
    );
    displayPlayers(filteredPlayers);
}

// プレイヤー選択
async function selectPlayer(playerName) {
    selectedPlayer = playerName;
    document.getElementById('selectedPlayerName').textContent = `${playerName} のインベントリ`;

    try {
        showLoadingInventory();

        const response = await fetch(`/api/inventory?player=${encodeURIComponent(playerName)}`);
        if (!response.ok) {
            throw new Error(`HTTP error! status: ${response.status}`);
        }

        const inventoryData = await response.json();
        displayInventory(inventoryData);

        // インベントリセクションを表示
        document.getElementById('inventorySection').style.display = 'block';
        document.getElementById('inventorySection').scrollIntoView({ behavior: 'smooth' });

    } catch (error) {
        console.error('インベントリの読み込みに失敗:', error);
        showError(`${playerName} のインベントリデータを読み込めませんでした。`);
    }
}

// インベントリデータを表示
function displayInventory(data) {
    if (!data.online) {
        document.getElementById('playerStats').innerHTML = `
            <div class="error">
                ${data.message || 'オフラインプレイヤーのデータは取得できません'}
            </div>
        `;
        clearInventoryDisplay();
        return;
    }

    // プレイヤーステータス表示
    displayPlayerStats(data);

    // 防具表示
    displayArmor(data.armor);

    // インベントリ表示
    displayInventoryItems(data.inventory);
}

// プレイヤーステータス表示
function displayPlayerStats(data) {
    const statsHtml = `
        <div class="stat-item">
            <div class="stat-label">体力</div>
            <div class="stat-value">${Math.round(data.health)}/${Math.round(data.maxHealth)}</div>
        </div>
        <div class="stat-item">
            <div class="stat-label">満腹度</div>
            <div class="stat-value">${data.foodLevel}/20</div>
        </div>
        <div class="stat-item">
            <div class="stat-label">レベル</div>
            <div class="stat-value">${data.level}</div>
        </div>
        <div class="stat-item">
            <div class="stat-label">経験値</div>
            <div class="stat-value">${Math.round(data.exp * 100)}%</div>
        </div>
    `;

    document.getElementById('playerStats').innerHTML = statsHtml;
}

// 防具表示
function displayArmor(armor) {
    const armorSlots = ['helmet', 'chestplate', 'leggings', 'boots'];

    armorSlots.forEach(slot => {
        const element = document.getElementById(slot);
        const material = armor[slot];

        if (material && material !== 'AIR') {
            element.className = 'item-slot has-item';
            element.textContent = formatMaterialName(material);
            element.title = formatMaterialName(material);
        } else {
            element.className = 'item-slot';
            element.textContent = '';
            element.title = '空';
        }
    });
}

// インベントリアイテム表示
function displayInventoryItems(inventory) {
    // ホットバー（スロット0-8）
    const hotbar = document.getElementById('hotbar');
    hotbar.innerHTML = '';

    for (let i = 0; i < 9; i++) {
        const item = inventory.find(item => item.slot === i);
        const slot = createItemSlot(item);
        hotbar.appendChild(slot);
    }

    // メインインベントリ（スロット9-35）
    const inventoryGrid = document.getElementById('inventoryGrid');
    inventoryGrid.innerHTML = '';

    for (let i = 9; i < 36; i++) {
        const item = inventory.find(item => item.slot === i);
        const slot = createItemSlot(item);
        inventoryGrid.appendChild(slot);
    }
}

// アイテムスロット要素を作成
function createItemSlot(item) {
    const slot = document.createElement('div');
    slot.className = 'item-slot';

    if (item && item.material !== 'AIR' && item.amount > 0) {
        slot.className += ' has-item';
        slot.textContent = formatMaterialName(item.material);
        slot.title = `${item.displayName || formatMaterialName(item.material)} x${item.amount}`;

        if (item.amount > 1) {
            const amountSpan = document.createElement('span');
            amountSpan.className = 'item-amount';
            amountSpan.textContent = item.amount;
            slot.appendChild(amountSpan);
        }
    } else {
        slot.title = '空のスロット';
    }

    return slot;
}

// マテリアル名をフォーマット
function formatMaterialName(material) {
    if (!material || material === 'AIR') return '';

    // マテリアル名を日本語に変換（簡易版）
    const materialMap = {
        'DIAMOND_SWORD': 'ダイヤの剣',
        'DIAMOND_PICKAXE': 'ダイヤのピッケル',
        'DIAMOND_AXE': 'ダイヤの斧',
        'DIAMOND_SHOVEL': 'ダイヤのシャベル',
        'DIAMOND_HOE': 'ダイヤのクワ',
        'DIAMOND_HELMET': 'ダイヤのヘルメット',
        'DIAMOND_CHESTPLATE': 'ダイヤのチェストプレート',
        'DIAMOND_LEGGINGS': 'ダイヤのレギンス',
        'DIAMOND_BOOTS': 'ダイヤのブーツ',
        'IRON_SWORD': '鉄の剣',
        'IRON_PICKAXE': '鉄のピッケル',
        'IRON_AXE': '鉄の斧',
        'IRON_SHOVEL': '鉄のシャベル',
        'IRON_HOE': '鉄のクワ',
        'IRON_HELMET': '鉄のヘルメット',
        'IRON_CHESTPLATE': '鉄のチェストプレート',
        'IRON_LEGGINGS': '鉄のレギンス',
        'IRON_BOOTS': '鉄のブーツ',
        'COOKED_BEEF': '焼き牛肉',
        'BREAD': 'パン',
        'APPLE': 'リンゴ',
        'GOLDEN_APPLE': '金のリンゴ',
        'ENDER_PEARL': 'エンダーパール',
        'DIRT': '土',
        'STONE': '石',
        'COBBLESTONE': '丸石',
        'WOOD': '木材',
        'PLANKS': '板材'
    };

    return materialMap[material] || material.replace(/_/g, ' ');
}

// 日付フォーマット
function formatDate(timestamp) {
    if (!timestamp) return '不明';
    const date = new Date(timestamp);
    return date.toLocaleDateString('ja-JP') + ' ' + date.toLocaleTimeString('ja-JP');
}

// プレイヤー一覧を更新
function refreshPlayers() {
    loadPlayers();
}

// ローディング表示
function showLoading(message) {
    document.getElementById('playerList').innerHTML = `<p class="loading">${message}</p>`;
}

// インベントリローディング表示
function showLoadingInventory() {
    document.getElementById('playerStats').innerHTML = '<p class="loading">データを読み込み中...</p>';
    clearInventoryDisplay();
}

// エラー表示
function showError(message) {
    document.getElementById('playerList').innerHTML = `<div class="error">${message}</div>`;
}

// インベントリ表示をクリア
function clearInventoryDisplay() {
    // 防具スロットをクリア
    ['helmet', 'chestplate', 'leggings', 'boots'].forEach(slot => {
        const element = document.getElementById(slot);
        element.className = 'item-slot';
        element.textContent = '';
        element.title = '空';
    });

    // インベントリスロットをクリア
    document.getElementById('hotbar').innerHTML = '';
    document.getElementById('inventoryGrid').innerHTML = '';
}

// 定期的にデータを更新（30秒ごと）
setInterval(function() {
    if (selectedPlayer) {
        selectPlayer(selectedPlayer);
    }
}, 30000);