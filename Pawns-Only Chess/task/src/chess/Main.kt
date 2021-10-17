package chess

import kotlin.math.abs

sealed class CellT {
    abstract val direction: Int
    open fun isWalked(row: Int) = false

    operator fun not(): CellT = when (this) {
        Black -> White
        Empty -> Empty
        White -> Black
    }

    object Black : CellT() {
        override fun isWalked(row: Int) = row != 7
        override val direction: Int = -1
        override fun toString(): String = "B"
    }

    object White : CellT() {
        override fun isWalked(row: Int) = row != 2
        override fun toString(): String = "W"
        override val direction: Int = 1
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

    private val isBlack: Boolean = to.y < from.y
    private val isWhite: Boolean = to.y > from.y
    private val isInBoard: Boolean = listOf(from.x, from.y, to.x, to.y)
        .fold(true) { acc, i -> acc && i in 0..7 }
    private val isForward = from.x == to.x
    private val isOneForward = abs(from.y - to.y) == 1
    private val isOneLeft = (from.x - 1) == to.x
    private val isOneRight = (from.x + 1) == to.x
    private val vertDirection = if (isWhite) 1 else -1
    val isTwoForward = abs(from.y - to.y) == 2
    private val isInArea = (isOneLeft && isOneForward) xor
            (isOneRight && isOneForward) xor
            (isForward && isOneForward) xor (isForward && isTwoForward)
    val isEnPassant = isBlack && !isForward && from.y == 3 &&
            to.y == 2 || isWhite && !isForward && from.y == 4 && to.y == 5

    fun isCorrect(gameBoard: GameBoard): Boolean {
        when {
            !isInArea -> return false
            !isInBoard -> return false
        }
        val fromC = gameBoard[from]
        val toC = gameBoard[to]
        return when {
            fromC is CellT.Empty -> false
            fromC.direction != vertDirection -> false
            fromC.isWalked(from.num) && isTwoForward -> false
            isForward && isOneForward && toC.direction != 0 -> false
            isForward && isTwoForward && toC.direction != 0 && gameBoard[Position(
                from.letter, from.num + vertDirection
            )].direction != 0 -> false
            isForward -> true
            (!isForward) && toC.direction == (-fromC.direction) -> true
            (!isForward) && gameBoard.lastTurn.isTwoForward && gameBoard.lastTurn.to.x == to.x && isEnPassant -> true
            !isForward -> false
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
            when (y) {
                6 -> CellT.Black
                1 -> CellT.White
                else -> CellT.Empty
            }
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
        if (turn.isEnPassant && lastTurn.isTwoForward && lastTurn.to.x == turn.to.x)
            this[lastTurn.to] = CellT.Empty
        lastTurn = turn
    }

    fun processTurn(turn: Turn, currPlayer: Player): String? {
        val cellFrom = this[turn.from]
        when {
            cellFrom !is CellT.White && currPlayer is Player.White ->
                return "No white pawn at ${turn.from}"

            cellFrom !is CellT.Black && currPlayer is Player.Black ->
                return "No black pawn at ${turn.from}"

            !turn.isCorrect(this) ->
                return "Invalid Input"
        }

        performTurn(turn)
        return null
    }

    private inline fun <reified Color : CellT> countByColor() = rawBoard.sumOf {
        it.filterIsInstance<Color>().size
    }

    private fun haveCorrectTurns(position: Position): Boolean {
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

    private inline fun <reified Color> haveCorrectTurns(): Boolean {
        for ((y, it1) in rawBoard.withIndex())
            for ((x, it2) in it1.withIndex())
                if (it2 is Color && haveCorrectTurns(Position(x, y)))
                    return true
        return false
    }

    fun checkEndOfGame(): Winning {
        rawBoard[0].forEach {
            if (it is CellT.Black)
                return it.win()
        }
        rawBoard[7].forEach {
            if (it is CellT.White)
                return it.win()
        }
        val b = countByColor<CellT.Black>()
        if (b == 0) return (!CellT.Black).win()
        val w = countByColor<CellT.White>()
        if (w == 0) return (!CellT.White).win()

        if (!haveCorrectTurns<CellT.White>())
            return Winning.Stalemate
        if (!haveCorrectTurns<CellT.Black>())
            return Winning.Stalemate

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
    sealed class Win(private val str: String) : Winning() {
        override fun toString() = str
    }

    operator fun not(): Winning = when (this) {
        InProgress -> InProgress
        White -> Black
        Black -> White
        Stalemate -> Stalemate
    }

    object White : Win("White wins!")
    object Black : Win("Black wins!")
    object Stalemate : Win("Stalemate!")

    object InProgress : Winning()
}

fun CellT.win() = when (this) {
    CellT.Black -> Winning.Black
    CellT.White -> Winning.White
    CellT.Empty -> throw IllegalArgumentException("Impossible")
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
