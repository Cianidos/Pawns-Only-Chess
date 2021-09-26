package chess

import java.util.*
import kotlin.math.*

sealed class CellT {
    abstract val direction: Int

    sealed class Chess : CellT() {
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

class EvenLine {
    override fun toString(): String {
        return "  +---+---+---+---+---+---+---+---+"
    }
}

class OddLine(
    private val rowNum: Int,
    private val row: Collection<CellT>
) {
    override fun toString(): String {
        return row.joinToString(" | ", "$rowNum | ", " |")
    }
}

class LettersLine {
    override fun toString(): String {
        return "abcdefgh"
            .split("")
            .joinToString("   ", " ", "  ")
    }
}

class BoardString(
    private val board: Collection<Collection<CellT>>
) {
    private companion object {
        val evenLine = "\n" + EvenLine().toString() + "\n"
    }

    override fun toString(): String {
        return board.reversed()
            .mapIndexed { idx, row ->
                OddLine(board.size - idx, row).toString()
            }.joinToString(
                evenLine, evenLine,
                evenLine + LettersLine().toString() + "\n"
            )
    }
}

class GameInput(question: String? = null) {
    var answer: String

    init {
        if (question != null)
            println(question)
        answer = (readLine()!!).trim()
    }
}

class PlayersNames {
    val playerOne: String = GameInput("First Player's name:").answer
    val playerTwo: String = GameInput("Second Player's name:").answer
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
    val isInBoard: Boolean = listOf(from.x, from.y, to.x, to.y).fold(true) {acc, i -> acc && i in 0..7 }
    val isOnlyForward: Boolean = from.x == to.x
    val isForward: Boolean = abs(from.y - to.y) == 1
    val isDouble: Boolean = abs(from.y - to.y) == 2
    val isToLeft: Boolean = (from.x - 1) == to.x
    val isToRight: Boolean = (from.x + 1) == to.x
    val isEnPassant: Boolean = isBlack && !isOnlyForward && from.y == 3 && to.y == 2 ||
            isWhite && !isOnlyForward && from.y == 4 && to.y == 5
    val isInArea: Boolean = (isToLeft && isForward) xor (isToRight && isForward) xor (isOnlyForward && isForward) xor (isOnlyForward && isDouble)
    val vertDirection = if(isWhite) 1 else -1
    val horDirection = if(isOnlyForward) 0 else (if (isToLeft) -1 else 1)

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
            isOnlyForward && isDouble && toC.direction != 0 && gameBoard[Position(from.letter, from.num + vertDirection)].direction != 0 -> false
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

    val rowBoard = MutableList(size) { y ->
        MutableList(size) {
            if (y == 6) CellT.Chess.Black() else
                (if (y == 1) CellT.Chess.White() else CellT.Empty)
        }
    }

    var lastTurn: Turn = Turn("a1a1")

    operator fun get(position: Position) = rowBoard[position.y][position.x]
    private operator fun set(position: Position, value: CellT) {
        rowBoard[position.y][position.x] = value
    }


    private fun performTurn(turn: Turn) {
        this[turn.to] = this[turn.from]
        this[turn.from] = CellT.Empty
        (this[turn.to] as CellT.Chess).walked += 1
        if (turn.isEnPassant && lastTurn.isDouble && lastTurn.to.x == turn.to.x)
            this[lastTurn.to] = CellT.Empty
        lastTurn = turn
    }

    fun processTurn(turn: Turn, currPlayer: Player): Optional<String> {
        val cellFrom = this[turn.from]
        when {
            (cellFrom is CellT.Empty || cellFrom is CellT.Chess.Black)
                    && currPlayer is Player.White ->
                return Optional.of("No white pawn at ${turn.from}")

            (cellFrom is CellT.Empty || cellFrom is CellT.Chess.White)
                    && currPlayer is Player.Black ->
                return Optional.of("No black pawn at ${turn.from}")

            !turn.isCorrect(this) ->
                return Optional.of("Invalid Input")
        }

        performTurn(turn)
        return Optional.empty()
    }

    private fun countBlackAndWhite(): Pair<Int, Int> {
        var b = 0
        var w = 0
        rowBoard.forEach {
            it.forEach{ it2 ->
                if (it2 is CellT.Chess.White) w += 1
                if (it2 is CellT.Chess.Black) b += 1
            }
        }
        return b to w
    }

    fun haveCorrectTurns(position: Position): Boolean {
        val candidates = mutableListOf<Position>()
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

    inline fun<reified Color> haveCorrectTurns(): Boolean {
        rowBoard.forEachIndexed { y, it1 ->
            it1.forEachIndexed { x, it2 ->
                if (it2 is Color)
                    if (haveCorrectTurns(Position(x, y)))
                        return@haveCorrectTurns true
            }
        }
        return false
    }

    fun checkEndOfGame(): Winning {
        rowBoard[0].forEach {
            if (it is CellT.Chess.Black)
                return Winning.Black
        }
        rowBoard[7].forEach {
            if (it is CellT.Chess.White)
                return Winning.White
        }
        val (b, w) = countBlackAndWhite()
        if (b == 0) return Winning.White
        if (w == 0) return Winning.Black

        if (!haveCorrectTurns<CellT.Chess.White>())
            return Winning.Stalemate
        if (!haveCorrectTurns<CellT.Chess.Black>())
            return Winning.Stalemate

        return Winning.InProgress
    }
}


sealed class Player {
    object White : Player()
    object Black : Player()

    operator fun not(): Player = when (this) {
        Black -> White
        White -> Black
    }
}

sealed class GameAction {
    companion object {
        private val exit = "exit".toRegex()
        private val turn = "[a-h][1-8][a-h][1-8]".toRegex()
        fun parseAction(str: String): GameAction = when {
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
    object White : Winning()
    object Black : Winning()
    object Stalemate : Winning()
    object InProgress : Winning()
}

class Game {
    private var currPlayer: Player = Player.White
    private val currPlayerName: String
        get() = when (currPlayer) {
            Player.White -> names.playerOne
            Player.Black -> names.playerTwo
        }

    private var names: PlayersNames
    private var board: GameBoard

    init {
        GameOutput("Pawns-Only Chess")
        names = PlayersNames()
        board = GameBoard()
        GameOutput(BoardString(board.rowBoard).toString())
    }

    fun start() {
        loop@ while (true) {
            val action = GameAction.parseAction(
                GameInput("${currPlayerName}'s turn:").answer
            )
            when (action) {
                GameAction.Error -> GameOutput("Invalid Input")
                GameAction.ExitAction -> {
                    GameOutput("Bye!")
                    break@loop
                }
                is GameAction.TurnAction -> {
                    val result = board.processTurn(action.turn, currPlayer)
                    if (result.isPresent) {
                        GameOutput(result.get())
                        continue@loop
                    }
                    GameOutput(BoardString(board.rowBoard).toString())
                    when (board.checkEndOfGame()){
                        Winning.Black -> {
                            GameOutput("Black Wins!")
                            GameOutput("Bye!")
                            break@loop
                        }
                        Winning.White -> {
                            GameOutput("White Wins!")
                            GameOutput("Bye!")
                            break@loop
                        }
                        Winning.Stalemate -> {
                            GameOutput("Stalemate!")
                            GameOutput("Bye!")
                            break@loop
                        }
                        Winning.InProgress -> { }
                    }
                    currPlayer = !currPlayer
                }
            }
        }
    }
}

fun main() {
    Game().start()
}