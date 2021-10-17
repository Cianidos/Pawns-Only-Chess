package chess

import kotlin.math.abs

sealed class CellT {
    abstract val direction: Int

    sealed class Chess : CellT() {
        // TODO: direction kludge refactor
        var walked: Int = 0

        class Black : Chess() {
            override val direction: Int = -1
            override fun toString(): String = "B"
        }

        class White : Chess() {
            override fun toString(): String = "W"
            override val direction: Int = 1
        }
    }

    object Empty : CellT() {
        override val direction: Int = 0
        override fun toString(): String = " "
    }
}


class BoardString(private val board: GameBoard) {
    private companion object {
        const val evenLine = "\n  +---+---+---+---+---+---+---+---+\n"
        const val lettersLine = " a   b   c   d   e   f   g   h  "

        fun oddLine(rowNum: Int, row: Collection<CellT>) =
            row.joinToString(" | ", "$rowNum | ", " |")
    }

    override fun toString() = board.rawBoard
        .reversed()
        .mapIndexed { idx, row -> oddLine(board.rawBoard.size - idx, row) }
        .joinToString(evenLine, evenLine, evenLine + lettersLine + "\n")
}

fun gameInput(question: String? = null): String {
    question?.let { println(it) }
    return (readLine()!!).trim()
}

class GameOutput(message: String) {
    init {
        println(message)
    }
}

data class Position(val letter: Char, val num: Int) {
    constructor(x_: Int, y_: Int) : this('a' + x_, y_ + 1)

    val x: Int = (letter - 'a')
    val y: Int = num - 1

    fun plusOneL(): Position = Position(letter + 1, num)
    fun plusOneN(): Position = Position(letter, num + 1)

    fun minusOneL(): Position = Position(letter - 1, num)
    fun minusOneN(): Position = Position(letter, num - 1)

    override fun toString(): String {
        return "$letter$num"
    }
}

data class Turn(val from: Position, val to: Position) {
    private companion object {
        fun parseTurn(str: String): Turn = Turn(
            Position(str[0], str[1].digitToInt()),
            Position(str[2], str[3].digitToInt())
        )
    }

    constructor(other: Turn) : this(other.from, other.to)
    constructor(str: String) : this(parseTurn(str))

    val isBlack: Boolean = to.y < from.y
    val isWhite: Boolean = to.y > from.y
    val isInBoard: Boolean = listOf(
        from.x,
        from.y,
        to.x,
        to.y
    ).fold(true) { acc, i -> acc && i in 0..7 }
    val isOnlyForward: Boolean = from.x == to.x
    val isForward: Boolean = abs(from.y - to.y) == 1
    val isDouble: Boolean = abs(from.y - to.y) == 2
    val isToLeft: Boolean = (from.x - 1) == to.x
    val isToRight: Boolean = (from.x + 1) == to.x
    val isEnPassant: Boolean =
        isBlack && !isOnlyForward && from.y == 3 && to.y == 2 ||
                isWhite && !isOnlyForward && from.y == 4 && to.y == 5
    val isInArea: Boolean =
        (isToLeft && isForward) xor (isToRight && isForward) xor (isOnlyForward && isForward) xor (isOnlyForward && isDouble)
    val vertDirection = if (isWhite) 1 else -1
    val horDirection = if (isOnlyForward) 0 else (if (isToLeft) -1 else 1)

    fun isCorrect(gameBoard: GameBoard): Boolean {
        when {
            !isInArea -> return false
            !isInBoard -> return false
        }
        val fromC = gameBoard[from]
        val toC = gameBoard[to]
        return when {
            fromC !is CellT.Chess -> false
            fromC.direction != vertDirection -> false
            fromC.walked != 0 && isDouble -> false
            isOnlyForward && isForward && toC.direction != 0 -> false
            isOnlyForward && isDouble && toC.direction != 0 && gameBoard[Position(
                from.letter,
                from.num + vertDirection
            )].direction != 0 -> false
            isOnlyForward -> true
            (!isOnlyForward) && toC.direction == (-fromC.direction) -> true
            (!isOnlyForward) && gameBoard.lastTurn.isDouble && gameBoard.lastTurn.to.x == to.x && isEnPassant -> true
            !isOnlyForward -> false
            else -> false
        }
    }
}

class GameBoard {
    companion object {
        const val size = 8
    }

    val rawBoard = MutableList(size) { y ->
        MutableList(size) {
            if (y == 6) CellT.Chess.Black() else
                (if (y == 1) CellT.Chess.White() else CellT.Empty)
        }
    }

    var lastTurn: Turn = Turn("a1a1")

    operator fun get(position: Position) = rawBoard[position.y][position.x]

    private operator fun set(position: Position, value: CellT) {
        rawBoard[position.y][position.x] = value
    }


    private fun performTurn(turn: Turn) {
        this[turn.to] = this[turn.from]
        this[turn.from] = CellT.Empty
        (this[turn.to] as CellT.Chess).walked += 1
        if (turn.isEnPassant && lastTurn.isDouble && lastTurn.to.x == turn.to.x)
            this[lastTurn.to] = CellT.Empty
        lastTurn = turn
    }

    fun processTurn(turn: Turn, currPlayer: Player): String? {
        val cellFrom = this[turn.from]
        when {
            (cellFrom is CellT.Empty || cellFrom is CellT.Chess.Black)
                    && currPlayer is Player.White ->
                return "No white pawn at ${turn.from}"

            (cellFrom is CellT.Empty || cellFrom is CellT.Chess.White)
                    && currPlayer is Player.Black ->
                return "No black pawn at ${turn.from}"

            !turn.isCorrect(this) ->
                return "Invalid Input"
        }

        performTurn(turn)
        return null
    }

    private inline fun <reified Color : CellT.Chess> countByColor(): Int {
        return rawBoard.sumOf {
            it.filterIsInstance<Color>().size
        }
    }

    fun haveCorrectTurns(position: Position): Boolean {
        position.run {
            for (letter_ in (letter - 1)..(letter + 1))
                for (num_ in (num - 1)..(num + 1)) {
                    val c = Position(letter_, num_)
                    if (c != this && Turn(this, c).isCorrect(this@GameBoard))
                        return@haveCorrectTurns true
                }
        }
        return false
    }

    inline fun <reified Color> haveCorrectTurns(): Boolean {
        rawBoard.forEachIndexed { y, it1 ->
            it1.forEachIndexed { x, it2 ->
                if (it2 is Color)
                    if (haveCorrectTurns(Position(x, y)))
                        return@haveCorrectTurns true
            }
        }
        return false
    }

    fun checkEndOfGame(): Winning {
        rawBoard[0].forEach {
            if (it is CellT.Chess.Black)
                return Winning.black
        }
        rawBoard[7].forEach {
            if (it is CellT.Chess.White)
                return Winning.white
        }
        val b = countByColor<CellT.Chess.Black>()
        val w = countByColor<CellT.Chess.White>()
        if (b == 0) return Winning.white
        if (w == 0) return Winning.black

        if (!haveCorrectTurns<CellT.Chess.White>())
            return Winning.stalemate
        if (!haveCorrectTurns<CellT.Chess.Black>())
            return Winning.stalemate

        return Winning.InProgress
    }
}


sealed class Player {
    abstract var name: String

    companion object {
        fun playersNames() {
            White.name = gameInput("First Player's name:")
            Black.name = gameInput("Second Player's name:")
        }
    }


    object White : Player() {
        override lateinit var name: String
    }

    object Black : Player() {
        override lateinit var name: String
    }

    operator fun not(): Player = when (this) {
        Black -> White
        White -> Black
    }
}

sealed class GameAction {
    companion object {
        private val exit = "exit".toRegex()
        private val turn = "[a-h][1-8][a-h][1-8]".toRegex()
        fun parse(str: String): GameAction = when {
            turn.matches(str) -> TurnAction(Turn(str))
            exit.matches(str) -> ExitAction
            else -> Error
        }
    }

    data class TurnAction(val turn: Turn) : GameAction()
    object ExitAction : GameAction()
    object Error : GameAction()
}

sealed class Winning {
    companion object {
        val white = Win("White wins!")
        val black = Win("Black wins!")
        val stalemate = Win("Stalemate!")
    }

    data class Win(private val str: String) : Winning() {
        override fun toString() = str
    }

    object InProgress : Winning()
}

class Game {
    private var currPlayer: Player = Player.White
    private val currPlayerName: String
        get() = currPlayer.name

    private var board: GameBoard

    init {
        GameOutput("Pawns-Only Chess")
        Player.playersNames()
        board = GameBoard()
        GameOutput(BoardString(board).toString())
    }

    fun start() {
        while (true) {
            val action = GameAction.parse(
                gameInput("${currPlayerName}'s turn:")
            )
            when (action) {
                GameAction.Error -> GameOutput("Invalid Input")
                GameAction.ExitAction -> {
                    GameOutput("Bye!"); break
                }
                is GameAction.TurnAction -> {
                    val it = board.processTurn(action.turn, currPlayer)
                    if (it != null) {
                        GameOutput(it)
                        continue
                    }
                    GameOutput(BoardString(board).toString())
                    if (checkEndOfGame()) break
                    currPlayer = !currPlayer
                }
            }
        }
    }

    private fun checkEndOfGame(): Boolean {
        when (val res = board.checkEndOfGame()) {
            is Winning.Win -> {
                GameOutput("${res}\nBye!")
                return true
            }
            Winning.InProgress -> Unit
        }
        return false
    }
}

fun main() {
    Game().start()
}
